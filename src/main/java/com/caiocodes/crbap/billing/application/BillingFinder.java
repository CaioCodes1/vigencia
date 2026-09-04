package com.caiocodes.crbap.billing.application;

import com.caiocodes.crbap.billing.domain.Billing;
import com.caiocodes.crbap.billing.domain.BillingId;
import com.caiocodes.crbap.billing.domain.BillingRepository;
import com.caiocodes.crbap.shared.application.ClientDirectory;
import com.caiocodes.crbap.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.crbap.shared.application.CurrentUser;
import com.caiocodes.crbap.shared.domain.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Carregar uma cobrança respeitando a carteira de quem chama.
 *
 * <p>Gêmeo do {@code ContractFinder}, e pelo mesmo motivo: "fora do escopo
 * responde 404, nunca 403" precisa morar num lugar só, senão um endpoint novo
 * esquece e abre o IDOR que os outros fecham.
 */
@Component
@RequiredArgsConstructor
public class BillingFinder {

    static final String READ_ALL = "billing:read_all";

    private final BillingRepository billings;
    private final ClientDirectory clients;
    private final CurrentUser currentUser;

    public Billing require(BillingId id) {
        return billings.findByIdInScope(id, scope())
                .orElseThrow(() -> new NotFoundException("Cobrança", id));
    }

    /**
     * Escrita: trava a linha antes de qualquer decisão.
     *
     * <p>Sem isso, dois pagamentos simultâneos sobre a mesma cobrança leriam o
     * mesmo saldo, os dois passariam pela checagem de "excede o saldo" e a
     * cobrança receberia mais dinheiro do que devia.
     */
    public Billing requireForUpdate(BillingId id) {
        Billing billing = billings.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Cobrança", id));
        UUID scope = scope();
        if (scope != null && !scope.equals(accountManagerOf(billing))) {
            throw new NotFoundException("Cobrança", id);
        }
        return billing;
    }

    public String clientNameOf(Billing billing) {
        return clients.findRef(billing.clientId())
                .map(ClientRef::legalName)
                .orElse(null);
    }

    public UUID scope() {
        return currentUser.scopeOrNull(READ_ALL);
    }

    private UUID accountManagerOf(Billing billing) {
        return clients.findRef(billing.clientId())
                .map(ClientRef::accountManagerId)
                .orElse(null);
    }
}
