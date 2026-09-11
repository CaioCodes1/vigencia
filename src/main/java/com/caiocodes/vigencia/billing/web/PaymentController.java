package com.caiocodes.vigencia.billing.web;

import com.caiocodes.vigencia.billing.application.BillingCommands;
import com.caiocodes.vigencia.billing.application.BillingDetail;
import com.caiocodes.vigencia.billing.application.RefundPaymentUseCase;
import com.caiocodes.vigencia.billing.web.dto.BillingDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estorno de pagamento.
 *
 * <p>Recurso próprio, e não {@code /billings/{id}/payments/{paymentId}}: quem
 * estorna tem na mão o id do pagamento, tirado de um extrato — e não sabe (nem
 * precisa saber) a qual cobrança ele pertence.
 *
 * <p>É {@code DELETE} com corpo, o que é incomum mas está no contrato público
 * desta API (nota 08): o verbo comunica "desfaz" e o motivo é obrigatório. O
 * que este {@code DELETE} <b>não</b> faz é apagar a linha — o pagamento é
 * marcado como estornado e o saldo da cobrança é recalculado.
 */
@Tag(name = "Pagamentos")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RefundPaymentUseCase refundPayment;

    @Operation(summary = "Estorna um pagamento — não apaga, marca e recalcula o saldo")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:refund')")
    public BillingDetail refund(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return refundPayment.execute(new BillingCommands.RefundPayment(id, body.reason()));
    }
}
