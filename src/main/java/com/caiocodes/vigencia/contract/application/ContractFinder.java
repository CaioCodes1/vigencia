package com.caiocodes.vigencia.contract.application;

import com.caiocodes.vigencia.contract.domain.Contract;
import com.caiocodes.vigencia.contract.domain.ContractId;
import com.caiocodes.vigencia.contract.domain.ContractRepository;
import com.caiocodes.vigencia.shared.application.ClientDirectory;
import com.caiocodes.vigencia.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.vigencia.shared.application.CurrentUser;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Carregar um contrato respeitando a carteira de quem chama.
 *
 * <p>Existe para que a decisão "fora do escopo responde 404, nunca 403" fique
 * num lugar só. Repetida em cada caso de uso, bastaria um esquecimento num
 * endpoint novo para abrir o IDOR que os outros seis fecham.
 *
 * <p>Não é um {@code *UseCase}: não tem transação nem regra de negócio própria,
 * é um colaborador dos casos de uso.
 */
@Component
@RequiredArgsConstructor
public class ContractFinder {

    static final String READ_ALL = "contract:read_all";

    private final ContractRepository contracts;
    private final ClientDirectory clients;
    private final CurrentUser currentUser;

    /** Leitura. */
    public Contract require(ContractId id) {
        return contracts.findByIdInScope(id, scope())
                .orElseThrow(() -> new NotFoundException("Contrato", id));
    }

    /**
     * Escrita: trava a linha antes de qualquer decisão.
     *
     * <p>O escopo é conferido <b>depois</b> de carregar, e não pela query: o
     * {@code SELECT ... FOR UPDATE} precisa ser por id, senão o join com
     * clientes travaria linhas de outra tabela junto.
     */
    public Contract requireForUpdate(ContractId id) {
        Contract contract = contracts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Contrato", id));
        UUID scope = scope();
        if (scope != null && !scope.equals(accountManagerOf(contract))) {
            throw new NotFoundException("Contrato", id);
        }
        return contract;
    }

    public String clientNameOf(Contract contract) {
        return clients.findRef(contract.clientId())
                .map(ClientRef::legalName)
                .orElse(null);
    }

    /** {@code null} quando o cliente sumiu — o chamador decide o que fazer. */
    public ClientRef clientRefOf(Contract contract) {
        return clients.findRef(contract.clientId()).orElse(null);
    }

    public UUID scope() {
        return currentUser.scopeOrNull(READ_ALL);
    }

    private UUID accountManagerOf(Contract contract) {
        return clients.findRef(contract.clientId())
                .map(ClientRef::accountManagerId)
                .orElse(null);
    }
}
