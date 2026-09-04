package com.caiocodes.crbap.contract.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * O mínimo que o módulo de contratos precisa saber sobre um cliente.
 *
 * <p>Quatro campos, e não o agregado {@code Client} inteiro: o contrato precisa
 * saber se o cliente existe, se está ativo, de quem é a carteira e como ele se
 * chama para montar a resposta. Nada além disso — e é por isso que a interface
 * mora aqui, no módulo que a consome, e não no módulo que a implementa.
 */
public interface ContractClientPort {

    Optional<ClientRef> findRef(UUID clientId);

    record ClientRef(UUID id, String legalName, boolean active, UUID accountManagerId) {
    }
}
