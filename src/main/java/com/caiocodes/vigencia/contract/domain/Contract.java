package com.caiocodes.vigencia.contract.domain;

import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractActivated;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractCancelled;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractCreated;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractExpired;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractRenewed;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractResumed;
import com.caiocodes.vigencia.contract.domain.ContractEvents.ContractSuspended;
import com.caiocodes.vigencia.shared.domain.AggregateRoot;
import com.caiocodes.vigencia.shared.domain.BillingCycle;
import com.caiocodes.vigencia.shared.domain.DateRange;
import com.caiocodes.vigencia.shared.domain.Money;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import com.caiocodes.vigencia.shared.domain.exception.IllegalStateTransitionException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contrato: o coração do sistema.
 *
 * <p>Três decisões que sustentam o desenho e não são óbvias olhando só os
 * campos:
 *
 * <ul>
 *   <li><b>Não existe {@code setStatus}.</b> O estado só muda por um método que
 *       carrega uma intenção de negócio ({@code activate}, {@code renew},
 *       {@code cancel}). É isso que impede alguém, daqui a dois anos, de
 *       reativar um contrato cancelado com uma linha inocente.</li>
 *   <li><b>Renovar não altera este contrato: cria o sucessor.</b> O histórico
 *       fiscal exige que o contrato anterior continue existindo com o valor e o
 *       período que teve. Mudar as datas no lugar apagaria o passado.</li>
 *   <li><b>{@code today} entra como parâmetro</b>, nunca {@code LocalDate.now()}
 *       aqui dentro. Sem isso, testar "expirado há 31 dias" exigiria mexer no
 *       relógio da máquina; com isso, é um argumento.</li>
 * </ul>
 *
 * <p>O cliente é referenciado por {@code UUID}, e não pelo tipo {@code ClientId}
 * do módulo {@code client}: agregados de módulos diferentes se conhecem por id.
 */
public class Contract extends AggregateRoot<ContractId> {

    /** Depois disso, renovar deixa de ser renovação e vira contrato novo. */
    public static final int MAX_DAYS_TO_RENEW_AFTER_EXPIRY = 30;

    private static final int MIN_REASON = 10;
    private static final int MAX_REASON = 500;
    private static final int MIN_TITLE = 3;
    private static final int MAX_BILLING_DAY = 28;
    private static final int MAX_GRACE_DAYS = 90;
    private static final Pattern RENEWAL_SUFFIX = Pattern.compile("^(.*)-R(\\d+)$");

    private final ContractId id;
    private final UUID clientId;
    private final String number;
    private String title;
    private String description;
    private DateRange period;
    private Money value;
    private BillingCycle cycle;
    private Integer billingDay;
    private int gracePeriodDays;
    private ContractStatus status;
    private boolean autoRenew;
    private ContractId previousContractId;
    private String cancellationReason;
    private Instant cancelledAt;
    private Instant activatedAt;
    private String idempotencyKey;
    private UUID createdBy;
    private long version;

    private Contract(ContractId id, UUID clientId, String number, String title,
                     String description, DateRange period, Money value, BillingCycle cycle,
                     Integer billingDay, int gracePeriodDays, boolean autoRenew) {
        this.id = Objects.requireNonNull(id, "o id é obrigatório");
        this.clientId = Objects.requireNonNull(clientId, "o cliente é obrigatório");
        this.number = requireNumber(number);
        this.title = requireTitle(title);
        this.description = blankToNull(description);
        this.period = Objects.requireNonNull(period, "o período de vigência é obrigatório");
        this.value = requirePositive(value);
        this.cycle = Objects.requireNonNull(cycle, "o ciclo de faturamento é obrigatório");
        this.billingDay = requireBillingDay(billingDay);
        this.gracePeriodDays = requireGrace(gracePeriodDays);
        this.autoRenew = autoRenew;
        this.status = ContractStatus.DRAFT;
    }

    /** Fábrica: é o único caminho para nascer um contrato, sempre em DRAFT. */
    public static Contract create(NewContract data) {
        Contract contract = new Contract(ContractId.newId(), data.clientId(), data.number(),
                data.title(), data.description(), data.period(), data.value(), data.cycle(),
                data.billingDay(), data.gracePeriodDays(), data.autoRenew());
        contract.createdBy = data.createdBy();
        contract.register(new ContractCreated(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), contract.id.value(), data.clientId(),
                contract.number, data.value().amount(),
                data.value().currency().getCurrencyCode(),
                data.period().start(), data.period().end(), data.cycle().name()));
        return contract;
    }

    /** Só a camada de persistência deve chamar. */
    public static Contract rehydrate(ContractState state) {
        Contract contract = new Contract(state.id(), state.clientId(), state.number(),
                state.title(), state.description(), state.period(), state.value(), state.cycle(),
                state.billingDay(), state.gracePeriodDays(), state.autoRenew());
        contract.status = state.status();
        contract.previousContractId = state.previousContractId();
        contract.cancellationReason = state.cancellationReason();
        contract.cancelledAt = state.cancelledAt();
        contract.activatedAt = state.activatedAt();
        contract.idempotencyKey = state.idempotencyKey();
        contract.createdBy = state.createdBy();
        contract.version = state.version();
        return contract;
    }

    // =================================================================
    // Máquina de estados
    // =================================================================

    /** DRAFT → ACTIVE. */
    public void activate(Instant now) {
        requireStatus(ContractStatus.DRAFT, "ACTIVATE");
        this.status = ContractStatus.ACTIVE;
        this.activatedAt = now;
        register(new ContractActivated(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), clientId, number,
                value.amount(), value.currency().getCurrencyCode(),
                period.start(), period.end(), cycle.name(), billingDay, gracePeriodDays));
    }

    /**
     * ACTIVE|EXPIRED → RENEWED, criando o sucessor já ativo.
     *
     * <p>O contrato devolvido é um agregado <b>novo</b>: quem chama precisa
     * gravar os dois, e por isso o caso de uso faz as duas gravações na mesma
     * transação. Metade renovada seria pior que nenhuma.
     *
     * @param idempotencyKey chave da requisição, gravada no sucessor. É o que
     *                       permite repetir a chamada sem criar dois contratos
     */
    public Contract renew(DateRange newPeriod, Money newValue, LocalDate today, Instant now,
                          String idempotencyKey) {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.EXPIRED) {
            throw new IllegalStateTransitionException(status.name(), "RENEW");
        }
        if (status == ContractStatus.EXPIRED
                && period.daysUntilEnd(today) < -MAX_DAYS_TO_RENEW_AFTER_EXPIRY) {
            throw new BusinessRuleException("CONTRACT_TOO_OLD_TO_RENEW",
                    "Contrato expirado há mais de " + MAX_DAYS_TO_RENEW_AFTER_EXPIRY
                            + " dias: crie um contrato novo");
        }
        if (newPeriod.start().isBefore(period.end())) {
            throw new BusinessRuleException("OVERLAPPING_PERIOD",
                    "O novo período não pode começar antes do fim do contrato atual ("
                            + period.end() + ")");
        }

        Contract next = new Contract(ContractId.newId(), clientId, nextNumber(number), title,
                description, newPeriod, newValue, cycle, billingDay, gracePeriodDays, autoRenew);
        next.previousContractId = this.id;
        next.createdBy = this.createdBy;
        next.idempotencyKey = blankToNull(idempotencyKey);
        next.status = ContractStatus.ACTIVE;
        next.activatedAt = now;

        this.status = ContractStatus.RENEWED;
        register(new ContractRenewed(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), next.id.value(), clientId,
                this.number, next.number, newValue.amount(),
                newValue.currency().getCurrencyCode(),
                newPeriod.start(), newPeriod.end()));
        return next;
    }

    /** DRAFT|ACTIVE|SUSPENDED|EXPIRED → CANCELLED. */
    public void cancel(String reason, LocalDate today, Instant now) {
        if (status.isFinal()) {
            throw new IllegalStateTransitionException(status.name(), "CANCEL");
        }
        this.cancellationReason = requireReason(reason);
        this.status = ContractStatus.CANCELLED;
        this.cancelledAt = now;
        register(new ContractCancelled(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), clientId, number,
                cancellationReason, today));
    }

    /** ACTIVE → SUSPENDED. */
    public void suspend(String reason) {
        requireStatus(ContractStatus.ACTIVE, "SUSPEND");
        String motivo = requireReason(reason);
        this.status = ContractStatus.SUSPENDED;
        register(new ContractSuspended(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), clientId, number, motivo));
    }

    /** SUSPENDED → ACTIVE. */
    public void resume() {
        requireStatus(ContractStatus.SUSPENDED, "RESUME");
        this.status = ContractStatus.ACTIVE;
        register(new ContractResumed(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), clientId, number));
    }

    /**
     * ACTIVE → EXPIRED. Só o job diário chama.
     *
     * <p>Note que a data é conferida aqui, e não só na consulta do job: se a
     * query um dia mudar e trouxer um contrato vigente, o agregado recusa em
     * vez de expirar um contrato que ainda vale.
     */
    public void expire(LocalDate today) {
        requireStatus(ContractStatus.ACTIVE, "EXPIRE");
        if (!period.isExpiredOn(today)) {
            throw new BusinessRuleException("CONTRACT_STILL_VALID",
                    "Contrato ainda vigente até " + period.end());
        }
        this.status = ContractStatus.EXPIRED;
        register(new ContractExpired(ContractEvents.nextEventId(),
                ContractEvents.nowForMetadata(), id.value(), clientId, number, period.end()));
    }

    // =================================================================
    // Alterações
    // =================================================================

    /**
     * Edição só em DRAFT.
     *
     * <p>Depois de ativo, um contrato é um acordo assinado: mudar valor ou
     * vigência no lugar invalidaria as cobranças já geradas e a trilha de
     * auditoria. O caminho para mudar contrato ativo é renovar ou cancelar.
     */
    public void updateDraft(String newTitle, String newDescription, DateRange newPeriod,
                            Money newValue, BillingCycle newCycle, Integer newBillingDay,
                            Integer newGrace, Boolean newAutoRenew) {
        requireStatus(ContractStatus.DRAFT, "UPDATE");
        this.title = requireTitle(newTitle);
        this.description = blankToNull(newDescription);
        this.period = Objects.requireNonNull(newPeriod, "o período de vigência é obrigatório");
        this.value = requirePositive(newValue);
        this.cycle = Objects.requireNonNull(newCycle, "o ciclo de faturamento é obrigatório");
        this.billingDay = requireBillingDay(newBillingDay);
        if (newGrace != null) {
            this.gracePeriodDays = requireGrace(newGrace);
        }
        if (newAutoRenew != null) {
            this.autoRenew = newAutoRenew;
        }
    }

    // =================================================================
    // Consultas derivadas — calculadas, nunca guardadas
    // =================================================================

    public boolean isExpiringIn(int days, LocalDate today) {
        return status == ContractStatus.ACTIVE && period.daysUntilEnd(today) == days;
    }

    public long daysUntilEnd(LocalDate today) {
        return period.daysUntilEnd(today);
    }

    public boolean isRenewable(LocalDate today) {
        if (status == ContractStatus.ACTIVE) {
            return true;
        }
        return status == ContractStatus.EXPIRED
                && period.daysUntilEnd(today) >= -MAX_DAYS_TO_RENEW_AFTER_EXPIRY;
    }

    public boolean isActive() {
        return status == ContractStatus.ACTIVE;
    }

    /**
     * Número do sucessor: {@code CT-2026-0042} vira {@code CT-2026-0042-R1}, e
     * este vira {@code CT-2026-0042-R2}.
     *
     * <p>Derivar do anterior, em vez de pedir uma sequência nova, mantém a
     * cadeia legível para quem lê um boleto — e dispensa uma consulta ao banco
     * de dentro do domínio, que não teria como fazer.
     */
    static String nextNumber(String current) {
        Matcher matcher = RENEWAL_SUFFIX.matcher(current);
        if (matcher.matches()) {
            return matcher.group(1) + "-R" + (Integer.parseInt(matcher.group(2)) + 1);
        }
        return current + "-R1";
    }

    // =================================================================
    // Acessores
    // =================================================================

    @Override
    public ContractId id() {
        return id;
    }

    public UUID clientId() {
        return clientId;
    }

    public String number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public DateRange period() {
        return period;
    }

    public Money value() {
        return value;
    }

    public BillingCycle cycle() {
        return cycle;
    }

    public Integer billingDay() {
        return billingDay;
    }

    public int gracePeriodDays() {
        return gracePeriodDays;
    }

    public ContractStatus status() {
        return status;
    }

    public boolean autoRenew() {
        return autoRenew;
    }

    public ContractId previousContractId() {
        return previousContractId;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "Contract[" + number + ", " + status + ", " + period + "]";
    }

    // =================================================================
    // Invariantes
    // =================================================================

    private void requireStatus(ContractStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateTransitionException(status.name(), action);
        }
    }

    private static String requireNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new BusinessRuleException("O número do contrato é obrigatório");
        }
        return number.strip().toUpperCase();
    }

    private static String requireTitle(String title) {
        if (title == null || title.strip().length() < MIN_TITLE) {
            throw new BusinessRuleException(
                    "O título deve ter pelo menos " + MIN_TITLE + " caracteres");
        }
        return title.strip();
    }

    private static Money requirePositive(Money value) {
        Objects.requireNonNull(value, "o valor é obrigatório");
        if (!value.isPositive()) {
            throw new BusinessRuleException("O valor do contrato deve ser maior que zero");
        }
        return value;
    }

    private static Integer requireBillingDay(Integer day) {
        if (day == null) {
            return null;
        }
        if (day < 1 || day > MAX_BILLING_DAY) {
            // 29, 30 e 31 não existem em todo mês. Recusar aqui elimina a
            // classe inteira de bug "a cobrança de fevereiro não foi gerada".
            throw new BusinessRuleException(
                    "O dia de cobrança deve ficar entre 1 e " + MAX_BILLING_DAY);
        }
        return day;
    }

    private static int requireGrace(int days) {
        if (days < 0 || days > MAX_GRACE_DAYS) {
            throw new BusinessRuleException(
                    "A carência deve ficar entre 0 e " + MAX_GRACE_DAYS + " dias");
        }
        return days;
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.length() < MIN_REASON) {
            throw new BusinessRuleException("MISSING_REASON",
                    "Informe um motivo com pelo menos " + MIN_REASON + " caracteres");
        }
        if (normalized.length() > MAX_REASON) {
            throw new BusinessRuleException("MISSING_REASON",
                    "O motivo não pode passar de " + MAX_REASON + " caracteres");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
