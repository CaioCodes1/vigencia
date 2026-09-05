package com.caiocodes.crbap.client.application;

import com.caiocodes.crbap.audit.application.Auditable;
import com.caiocodes.crbap.client.application.port.ClientContractsPort;
import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientRepository;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import com.caiocodes.crbap.shared.domain.exception.NotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desativação lógica do cliente (RF-03) e reativação (RF-04).
 *
 * <p>Nada é apagado: contratos e cobranças precisam sobreviver ao cliente por
 * cinco anos, por retenção fiscal. O que muda é a situação — e, com ela, o
 * documento volta a ficar livre para um novo cadastro, porque o índice único é
 * parcial (`WHERE status = 'ACTIVE'`).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateClientUseCase {

    private final ClientRepository clients;
    private final ClientContractsPort contracts;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "Client", action = "DEACTIVATE", id = "#id.value()")
    public void deactivate(ClientId id) {
        Client client = clients.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente", id));

        // RF-03. Hoje a porta responde sempre "não" porque contratos só
        // existem na fase 4 — a regra fica escrita e testada desde já.
        if (contracts.hasActiveContracts(id)) {
            throw new BusinessRuleException("CLIENT_HAS_ACTIVE_CONTRACTS",
                    "Cliente com contrato ativo não pode ser desativado. "
                            + "Cancele ou deixe expirar os contratos primeiro.");
        }

        client.deactivate(clock.instant());
        clients.save(client);
        log.info("client.deactivated clientId={}", id);
    }

    @Transactional
    @Auditable(entity = "Client", action = "REACTIVATE", id = "#id.value()")
    public void reactivate(ClientId id) {
        Client client = clients.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente", id));

        // O documento pode ter sido reaproveitado por outro cadastro enquanto
        // este estava inativo. Reativar sem checar criaria dois clientes ativos
        // com o mesmo CPF — e o índice único derrubaria o INSERT com uma
        // mensagem que ninguém entende.
        if (clients.existsActiveWithDocument(client.document())) {
            throw new BusinessRuleException("DUPLICATE_DOCUMENT",
                    "Já existe outro cliente ativo com este documento");
        }

        client.reactivate();
        clients.save(client);
        log.info("client.reactivated clientId={}", id);
    }
}
