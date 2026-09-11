package com.caiocodes.vigencia.contract.web.dto;

import com.caiocodes.vigencia.contract.application.ContractCommands;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.shared.domain.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs de entrada da API de contratos.
 *
 * <p>Repare no que <b>não</b> existe aqui: {@code status}, {@code id} e
 * {@code previousContractId}. Se o cliente HTTP pudesse mandar o status, ele
 * escolheria o próprio estado e a máquina de estados do agregado viraria
 * decoração. E se pudesse mandar o {@code previousContractId}, montaria a
 * cadeia de renovações à mão — inclusive uma cadeia circular.
 */
public final class ContractDtos {

    private ContractDtos() {
    }

    public record CreateContractRequest(
            @NotNull UUID clientId,
            @Size(max = 40) @Schema(example = "CT-2026-0042",
                    description = "Opcional. Omitido, é gerado como CT-<ano>-<sequência>")
            String number,
            @NotBlank @Size(min = 3, max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$") @Schema(example = "BRL") String currency,
            @NotNull BillingCycle billingCycle,
            @Min(1) @Max(28) Integer billingDay,
            @Min(0) @Max(90) Integer gracePeriodDays,
            boolean autoRenew) {

        public ContractCommands.CreateContract toCommand() {
            return new ContractCommands.CreateContract(clientId, number, title, description,
                    startDate, endDate, amount, currency, billingCycle, billingDay,
                    gracePeriodDays, autoRenew);
        }
    }

    public record UpdateContractRequest(
            @NotBlank @Size(min = 3, max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull BillingCycle billingCycle,
            @Min(1) @Max(28) Integer billingDay,
            @Min(0) @Max(90) Integer gracePeriodDays,
            Boolean autoRenew) {

        public ContractCommands.UpdateContract toCommand(ContractId id) {
            return new ContractCommands.UpdateContract(id, title, description, startDate, endDate,
                    amount, currency, billingCycle, billingDay, gracePeriodDays, autoRenew);
        }
    }

    /**
     * O início do novo período não vem aqui: é sempre o dia seguinte ao fim do
     * anterior. {@code amount} é opcional — omitido, a renovação mantém o valor.
     */
    public record RenewContractRequest(
            @NotNull LocalDate endDate,
            @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$") String currency) {

        public ContractCommands.RenewContract toCommand(ContractId id, String idempotencyKey) {
            return new ContractCommands.RenewContract(id, endDate, amount, currency,
                    idempotencyKey);
        }
    }

    /**
     * Motivo de cancelamento ou suspensão.
     *
     * <p>O mínimo de 10 caracteres está aqui <b>e</b> no agregado. Não é
     * duplicação à toa: aqui devolve 400 com o campo apontado antes de abrir
     * transação; lá garante que nenhum outro caminho (job, importação) crie um
     * cancelamento sem justificativa.
     */
    public record ReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
    }
}
