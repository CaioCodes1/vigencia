package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os fatos que o contrato publica.
 *
 * <p>Todos num arquivo só porque são a mesma família e mudam juntos — a mesma
 * escolha de {@code ClientCommands} e {@code ClientDtos}.
 *
 * <p><b>Por que só tipos primitivos no corpo do evento</b> (e não {@code Money}
 * ou {@code DateRange}): o evento é um contrato com quem consome, e vai virar
 * JSON na outbox. Se ele carregasse os value objects do domínio, qualquer
 * refatoração interna quebraria o consumidor — que pode ser outro sistema, com
 * outro deploy. O evento é a fronteira; a fronteira é estável de propósito.
 *
 * <p><b>Sobre {@code Instant.now()} aqui:</b> o carimbo é metadado — registra
 * quando o fato foi anotado, e nenhuma regra de negócio se ramifica nele. Toda
 * decisão que depende de data recebe {@code today} como parâmetro, que é o que
 * a regra do {@code Clock} injetado existe para proteger.
 */
public final class ContractEvents {

    private ContractEvents() {
    }

    static UUID nextEventId() {
        return UUID.randomUUID();
    }

    static Instant nowForMetadata() {
        return Instant.now();
    }

    /** Nasceu, em DRAFT. Ainda não gera cobrança nem aviso. */
    public record ContractCreated(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number, BigDecimal amount, String currency,
            LocalDate startDate, LocalDate endDate, String billingCycle)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.created";
        }
    }

    /** Passou a valer. É o gatilho de geração de cobranças (fase 5). */
    public record ContractActivated(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number, BigDecimal amount, String currency,
            LocalDate startDate, LocalDate endDate, String billingCycle,
            Integer billingDay, int gracePeriodDays)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.activated";
        }
    }

    /**
     * Renovado. Carrega os dois ids porque quem consome precisa dos dois: o
     * antigo para encerrar as cobranças futuras, o novo para abrir as próximas.
     */
    public record ContractRenewed(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID newContractId, UUID clientId, String newNumber,
            BigDecimal amount, String currency,
            LocalDate startDate, LocalDate endDate)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.renewed";
        }
    }

    public record ContractCancelled(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number, String reason, LocalDate cancelledOn)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.cancelled";
        }
    }

    public record ContractExpired(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number, LocalDate endDate)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.expired";
        }
    }

    /**
     * O motivo da suspensão vive aqui, e não numa coluna: a tabela guarda o
     * <i>estado atual</i>, e o histórico de por que ele mudou é justamente o
     * que a sequência de eventos é.
     */
    public record ContractSuspended(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number, String reason)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.suspended";
        }
    }

    public record ContractResumed(
            UUID eventId, Instant occurredAt, UUID aggregateId,
            UUID clientId, String number)
            implements DomainEvent {

        @Override
        public String aggregateType() {
            return "Contract";
        }

        @Override
        public String eventType() {
            return "contract.resumed";
        }
    }
}
