package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.BillingId;
import com.caiocodes.crbap.billing.domain.BillingListItem;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import com.caiocodes.crbap.billing.domain.BillingSearchCriteria;
import com.caiocodes.crbap.billing.domain.BillingStatus;
import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** As leituras de cobrança: detalhe, busca e o painel de inadimplência. */
@Service
@RequiredArgsConstructor
public class GetBillingUseCase {

    private final BillingRepository billings;
    private final BillingFinder finder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BillingDetail byId(BillingId id) {
        var billing = finder.require(id);
        return BillingDetail.from(billing, finder.clientNameOf(billing), LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResult<BillingSummary> search(BillingStatus status, UUID clientId, UUID contractId,
                                             LocalDate dueFrom, LocalDate dueUntil,
                                             int page, int size) {
        LocalDate today = LocalDate.now(clock);
        PageResult<BillingListItem> result = billings.search(new BillingSearchCriteria(
                status, clientId, contractId, dueFrom, dueUntil, finder.scope(), page, size));
        return result.map(item -> BillingSummary.from(item, today));
    }

    /**
     * O painel de inadimplência.
     *
     * <p>Filtra por {@code OVERDUE}, e não por "vencimento no passado": as duas
     * coisas divergem no dia em que o job não roda, e a tela que o financeiro
     * usa para cobrar precisa mostrar o mesmo conjunto que gerou as
     * notificações. Divergir aí é ligar para quem já foi avisado e não ligar
     * para quem não foi.
     */
    @Transactional(readOnly = true)
    public PageResult<BillingSummary> overdue(UUID clientId, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        PageResult<BillingListItem> result = billings.search(new BillingSearchCriteria(
                BillingStatus.OVERDUE, clientId, null, null, null,
                finder.scope(), page, size));
        return result.map(item -> BillingSummary.from(item, today));
    }
}
