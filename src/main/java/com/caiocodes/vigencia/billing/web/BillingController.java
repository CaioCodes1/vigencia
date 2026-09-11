package com.caiocodes.vigencia.billing.web;

import com.caiocodes.vigencia.billing.application.BillingCommands;
import com.caiocodes.vigencia.billing.application.BillingDetail;
import com.caiocodes.vigencia.billing.application.BillingSummary;
import com.caiocodes.vigencia.billing.application.CancelBillingUseCase;
import com.caiocodes.vigencia.billing.application.CreateBillingUseCase;
import com.caiocodes.vigencia.billing.application.GetBillingUseCase;
import com.caiocodes.vigencia.billing.application.RegisterPaymentUseCase;
import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.BillingStatus;
import com.caiocodes.vigencia.billing.web.dto.BillingDtos.CreateBillingRequest;
import com.caiocodes.vigencia.billing.web.dto.BillingDtos.ReasonRequest;
import com.caiocodes.vigencia.billing.web.dto.BillingDtos.RegisterPaymentRequest;
import com.caiocodes.vigencia.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cobranças.
 *
 * <p>Só a cobrança <b>avulsa</b> é criada por aqui. As parcelas de contrato
 * nascem na ativação, na mesma transação — ver ADR-006 e
 * {@code ContractBillingPort}.
 */
@Tag(name = "Cobranças")
@RestController
@RequestMapping("/api/v1/billings")
@RequiredArgsConstructor
public class BillingController {

    private final CreateBillingUseCase createBilling;
    private final RegisterPaymentUseCase registerPayment;
    private final CancelBillingUseCase cancelBilling;
    private final GetBillingUseCase getBilling;

    @Operation(summary = "Cria uma cobrança avulsa")
    @PostMapping
    @PreAuthorize("hasAuthority('billing:create')")
    public ResponseEntity<BillingDetail> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBillingRequest body) {
        BillingDetail created = createBilling.execute(body.toCommand(idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/billings/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Lista e busca cobranças (dentro da carteira de quem chama)")
    @GetMapping
    @PreAuthorize("hasAuthority('billing:read')")
    public PageResponse<BillingSummary> search(
            @RequestParam(required = false) BillingStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(getBilling.search(status, clientId, contractId,
                dueDateFrom, dueDateTo, page, size));
    }

    @Operation(summary = "Painel de inadimplência")
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('billing:read')")
    public PageResponse<BillingSummary> overdue(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(getBilling.overdue(clientId, page, size));
    }

    @Operation(summary = "Detalha uma cobrança, com os pagamentos")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('billing:read')")
    public BillingDetail byId(@PathVariable UUID id) {
        return getBilling.byId(BillingId.of(id));
    }

    /**
     * Registra um pagamento.
     *
     * <p>Responde <b>201</b> quando registrou e <b>200</b> quando a mesma
     * {@code Idempotency-Key} já tinha registrado. O cabeçalho é obrigatório:
     * gateway de pagamento reenvia webhook, e o mesmo PIX contado duas vezes é
     * dinheiro que a empresa acha que recebeu.
     */
    @Operation(summary = "Registra um pagamento, total ou parcial")
    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('payment:create')")
    public ResponseEntity<BillingDetail> pay(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RegisterPaymentRequest body) {

        RegisterPaymentUseCase.PaymentResult result =
                registerPayment.execute(body.toCommand(BillingId.of(id), idempotencyKey));

        if (result.repeated()) {
            return ResponseEntity.ok(result.billing());
        }
        return ResponseEntity
                .created(URI.create("/api/v1/billings/" + id + "/payments/" + result.paymentId()))
                .body(result.billing());
    }

    @Operation(summary = "Cancela a cobrança — motivo obrigatório")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('billing:cancel')")
    public BillingDetail cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest body) {
        return cancelBilling.execute(
                new BillingCommands.CancelBilling(BillingId.of(id), body.reason()));
    }
}
