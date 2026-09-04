package com.caiocodes.crbap.shared.application;

import java.util.Optional;
import java.util.UUID;

/**
 * O mínimo que os outros módulos precisam saber sobre um cliente.
 *
 * <p>Quatro campos, e não o agregado {@code Client} inteiro: quem trabalha com
 * contrato ou cobrança precisa saber se o cliente existe, se está ativo, de quem
 * é a carteira e como ele se chama para montar a resposta. Nada além disso.
 *
 * <p><b>Por que no kernel compartilhado e não em cada módulo:</b> contratos e
 * cobranças precisam exatamente da mesma coisa. Duas portas idênticas, uma em
 * cada módulo, seriam duas implementações para manter em sincronia — e a segunda
 * inevitavelmente ganharia um campo que a primeira não tem. Quem implementa é o
 * módulo {@code client}, que é quem sabe responder.
 *
 * <p>A alternativa seria expor o {@code ClientRepository} diretamente. Não: ele
 * devolve o agregado com o documento decifrado e a lista de contatos, e um
 * módulo que só quer o nome do cliente não deveria ter acesso a nada disso.
 */
public interface ClientDirectory {

    Optional<ClientRef> findRef(UUID clientId);

    record ClientRef(UUID id, String legalName, boolean active, UUID accountManagerId) {
    }
}
