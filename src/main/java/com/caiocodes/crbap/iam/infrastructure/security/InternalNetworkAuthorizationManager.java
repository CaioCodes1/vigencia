package com.caiocodes.crbap.iam.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Libera uma rota apenas para quem chama de dentro da rede.
 *
 * <p>Existe por causa de um impasse concreto: o Prometheus precisa raspar
 * {@code /actuator/prometheus} de 15 em 15 segundos, para sempre, e as duas
 * saídas óbvias são ruins.
 *
 * <ul>
 *   <li><b>Exigir o token da API</b> — o access token dura 15 minutos e é
 *       rotacionado; um coletor não renova token. Sobraria criar uma credencial
 *       eterna de administrador e deixá-la num arquivo de configuração, que é
 *       pior do que o problema que resolve.</li>
 *   <li><b>{@code permitAll}</b> — as métricas contam quantos contratos a
 *       empresa tem, quanto está em atraso e quantos avisos falharam. É
 *       informação de negócio servida sem autenticação nenhuma.</li>
 * </ul>
 *
 * <p>A saída é a que a nota de observabilidade já previa: <b>rede interna</b>.
 * O coletor vive na mesma rede da aplicação; a internet não.
 *
 * <p><b>O endereço aqui é o da conexão TCP</b> ({@code getRemoteAddr()}), que
 * não se falsifica por cabeçalho — e é justamente por isso que
 * {@code server.forward-headers-strategy} ficou em {@code none} na fase 7. Em
 * {@code framework}, o Spring reescreveria esse valor a partir do
 * {@code X-Forwarded-For}, e aí qualquer um na internet se declararia
 * {@code 10.0.0.1} e leria as métricas. Ao publicar atrás de um proxy, as duas
 * configurações precisam ser revistas <b>juntas</b>.
 */
@Slf4j
public class InternalNetworkAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision NEGADO = new AuthorizationDecision(false);
    private static final AuthorizationDecision LIBERADO = new AuthorizationDecision(true);

    private final List<IpAddressMatcher> redes;

    public InternalNetworkAuthorizationManager(List<String> faixas) {
        List<IpAddressMatcher> compiladas = new ArrayList<>();
        for (String faixa : faixas) {
            if (faixa == null || faixa.isBlank()) {
                continue;
            }
            try {
                compiladas.add(new IpAddressMatcher(faixa.trim()));
            } catch (IllegalArgumentException e) {
                // Faixa torta vira "não confio nela". O contrário — subir
                // ignorando o erro e liberar tudo — é como se abre um endpoint
                // interno para a internet sem ninguém perceber.
                log.error("security.rede_interna_invalida faixa={} — sera ignorada", faixa);
            }
        }
        this.redes = List.copyOf(compiladas);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        String origem = context.getRequest().getRemoteAddr();
        if (origem == null) {
            return NEGADO;
        }
        for (IpAddressMatcher rede : redes) {
            if (rede.matches(origem)) {
                return LIBERADO;
            }
        }
        return NEGADO;
    }
}
