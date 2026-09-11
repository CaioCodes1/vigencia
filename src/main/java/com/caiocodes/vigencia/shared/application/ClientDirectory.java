package com.caiocodes.vigencia.shared.application;

import java.util.Optional;
import java.util.UUID;

/**
 * O mínimo que os outros módulos precisam saber sobre um cliente.
 *
 * <p>Poucos campos, e não o agregado {@code Client} inteiro: quem trabalha com
 * contrato, cobrança ou notificação precisa saber se o cliente existe, se está
 * ativo, de quem é a carteira, como ele se chama e para onde escrever. Nada
 * além disso — o documento cifrado e a lista completa de contatos ficam de
 * fora, e é essa a diferença entre uma porta e um repositório exposto.
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

    /**
     * @param primaryContactEmail e-mail do contato principal, ou nulo quando o
     *                             cliente não tem nenhum cadastrado. Nesse caso
     *                             o aviso vai para o {@code email} do cadastro
     */
    record ClientRef(UUID id, String legalName, boolean active, UUID accountManagerId,
                     String email, String primaryContactEmail) {

        /** Para onde escrever, na ordem de preferência do negócio. */
        public String notificationEmail() {
            return primaryContactEmail == null ? email : primaryContactEmail;
        }
    }
}
