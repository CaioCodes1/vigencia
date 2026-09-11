package com.caiocodes.vigencia.billing.web.dto;

import com.caiocodes.vigencia.billing.application.BillingCommands;
import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs de entrada da API de cobranças.
 *
 * <p>O que <b>não</b> existe aqui: {@code status}, {@code installment} e
 * {@code totalInstallments}. Status vem do saldo, e parcela é gerada pela
 * ativação do contrato — se a API pudesse criar "3 de 12" à mão, o total cobrado
 * deixaria de bater com o valor contratado.
 */
public final class BillingDtos {

    private BillingDtos() {
    }

    /** Cobrança avulsa. {@code contractId} é opcional e só serve de vínculo. */
    public record CreateBillingRequest(
            @NotNull UUID clientId,
            UUID contractId,
            @NotBlank @Size(max = 80) @Schema(example = "Multa contratual - atraso set/2026")
            String reference,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$") @Schema(example = "BRL") String currency,
            @NotNull LocalDate dueDate,
            @Size(max = 2000) String notes) {

        public BillingCommands.CreateBilling toCommand(String idempotencyKey) {
            return new BillingCommands.CreateBilling(clientId, contractId, reference, amount,
                    currency, dueDate, notes, idempotencyKey);
        }
    }

    /**
     * @param paidAt quando o dinheiro entrou. Vem do extrato ou do gateway, e
     *               por isso é do chamador — não é "agora"
     */
    public record RegisterPaymentRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull PaymentMethod method,
            @NotNull Instant paidAt,
            @Size(max = 100) @Schema(example = "E12345678") String externalId) {

        public BillingCommands.RegisterPayment toCommand(BillingId id, String idempotencyKey) {
            return new BillingCommands.RegisterPayment(id, amount, currency, method, paidAt,
                    externalId, idempotencyKey);
        }
    }

    /** Motivo de cancelamento ou de estorno. */
    public record ReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
    }
}
