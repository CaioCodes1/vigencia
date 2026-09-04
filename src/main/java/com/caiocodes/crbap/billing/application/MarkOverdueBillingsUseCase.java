package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.BillingId;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A varredura diária de inadimplência (RF-19).
 *
 * <p>Mesmo desenho da varredura de vencimentos: uma transação por cobrança, sem
 * {@code @Transactional} neste método, e a decisão de "essa aqui mudou de
 * estado?" dentro do agregado — que responde {@code false} em silêncio quando
 * não se aplica, porque num lote de milhares esse é o caso normal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkOverdueBillingsUseCase {

    private final BillingRepository billings;
    private final BillingOverdueMarker marker;
    private final Clock clock;

    /** @return quantas cobranças passaram para OVERDUE */
    public int execute() {
        LocalDate today = LocalDate.now(clock);
        List<BillingId> candidatas = billings.findOpenPastDue(today);
        if (candidatas.isEmpty()) {
            log.debug("billing.overdue_scan data={} nada vencido", today);
            return 0;
        }

        int marcadas = 0;
        for (BillingId id : candidatas) {
            try {
                if (marker.mark(id, today)) {
                    marcadas++;
                }
            } catch (RuntimeException e) {
                log.error("billing.overdue.falhou billingId={}", id, e);
            }
        }
        log.info("billing.overdue_scan data={} candidatas={} marcadas={}",
                today, candidatas.size(), marcadas);
        return marcadas;
    }
}
