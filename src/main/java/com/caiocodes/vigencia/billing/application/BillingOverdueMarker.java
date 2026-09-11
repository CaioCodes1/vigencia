package com.caiocodes.vigencia.billing.application;

import com.caiocodes.vigencia.billing.domain.Billing;
import com.caiocodes.vigencia.billing.domain.BillingId;
import com.caiocodes.vigencia.billing.domain.BillingRepository;
import com.caiocodes.vigencia.shared.application.DomainEventRecorder;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca <b>uma</b> cobrança como vencida, em transação própria.
 *
 * <p>Classe separada pelo mesmo motivo do {@code ContractExpirer}:
 * {@code @Transactional(REQUIRES_NEW)} num método chamado de outro método da
 * mesma classe é silenciosamente ignorado pelo proxy do Spring, e o lote inteiro
 * voltaria a compartilhar uma transação.
 */
@Component
@RequiredArgsConstructor
public class BillingOverdueMarker {

    private final BillingRepository billings;
    private final DomainEventRecorder events;

    /** @return true se a cobrança mudou de estado */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mark(BillingId id, LocalDate today) {
        Billing billing = billings.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Cobrança sumiu: " + id));
        if (!billing.markOverdue(today)) {
            // Alguém pagou entre a consulta e o lock. Caso normal, não erro.
            return false;
        }
        Billing saved = billings.save(billing);
        events.record(saved);
        return true;
    }
}
