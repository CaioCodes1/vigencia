package com.caiocodes.crbap.client.infrastructure;

import com.caiocodes.crbap.client.application.port.ClientContractsPort;
import com.caiocodes.crbap.client.domain.ClientId;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória da porta de contratos: <b>ainda não existem
 * contratos no sistema</b> (fase 4).
 *
 * <p>Ela existe para que a regra RF-03 já esteja escrita e testada no caso de
 * uso, em vez de virar um "lembrar de implementar depois" que ninguém lembra.
 * Na fase 4, esta classe é <b>apagada</b> e o módulo {@code contract} passa a
 * implementar a porta.
 */
@Component
public class NoContractsYetAdapter implements ClientContractsPort {

    @Override
    public boolean hasActiveContracts(ClientId clientId) {
        return false;
    }
}
