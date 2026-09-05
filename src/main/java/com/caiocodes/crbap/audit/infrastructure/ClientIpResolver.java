package com.caiocodes.crbap.audit.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * De qual IP veio a requisição — sem acreditar em quem diz de onde veio.
 *
 * <p><b>O problema:</b> {@code X-Forwarded-For} é um cabeçalho comum, e
 * cabeçalho é entrada do usuário. Qualquer um pode mandar
 * {@code X-Forwarded-For: 8.8.8.8} e, num código ingênuo, é isso que a
 * auditoria grava. A trilha passa a apontar para o IP que o invasor escolher —
 * pior que não ter IP nenhum, porque parece prova.
 *
 * <p><b>A regra aqui:</b> o cabeçalho só vale se o <i>peer</i> — o endereço
 * TCP de quem realmente abriu a conexão, que não se falsifica — for um proxy
 * declarado em {@code crbap.audit.trusted-proxies}. Sem proxy configurado (o
 * padrão), o cabeçalho é ignorado por completo e vale o peer.
 *
 * <p><b>Por que da direita para a esquerda:</b> a lista do XFF cresce por
 * acréscimo — cada proxy anexa o endereço de quem falou com ele. Só a parte
 * final foi escrita pela nossa infraestrutura; tudo antes disso pode ter vindo
 * pronto do cliente. Andando de trás para frente e pulando os proxies
 * conhecidos, o primeiro endereço não confiável é o mais próximo do cliente que
 * ainda é atestado por alguém em quem confiamos. Ler o primeiro da lista — o
 * jeito que aparece na maioria dos exemplos — é exatamente ler o que o cliente
 * escreveu.
 *
 * <p>Relacionado: {@code server.forward-headers-strategy} vem como
 * {@code none}. Em {@code framework}, o Spring reescreve o próprio
 * {@code getRemoteAddr()} a partir do XFF — e aí nem o peer sobra como fonte
 * confiável. Só ligue isso onde existir proxy de verdade na frente.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String XFF = "X-Forwarded-For";
    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern IPV6 =
            Pattern.compile("[0-9A-Fa-f:]{2,45}");

    private final List<IpAddressMatcher> proxiesConfiaveis;

    public ClientIpResolver(AuditProperties properties) {
        this.proxiesConfiaveis = compilar(properties.trustedProxies());
    }

    public String resolve(HttpServletRequest request) {
        String peer = normalizar(request.getRemoteAddr());
        if (proxiesConfiaveis.isEmpty() || !ehProxyConfiavel(peer)) {
            return peer;
        }

        String cabecalho = request.getHeader(XFF);
        if (cabecalho == null || cabecalho.isBlank()) {
            return peer;
        }

        String[] enderecos = cabecalho.split(",");
        for (int i = enderecos.length - 1; i >= 0; i--) {
            String candidato = normalizar(enderecos[i].trim());
            if (candidato == null) {
                // Lista malformada é motivo para desconfiar dela inteira, não
                // para pular a entrada ruim e seguir lendo o resto.
                return peer;
            }
            if (!ehProxyConfiavel(candidato)) {
                return candidato;
            }
        }
        // Só proxies conhecidos na lista: a requisição nasceu dentro de casa.
        return peer;
    }

    private boolean ehProxyConfiavel(String endereco) {
        if (endereco == null) {
            return false;
        }
        return proxiesConfiaveis.stream().anyMatch(matcher -> matcher.matches(endereco));
    }

    /**
     * Tira colchetes e porta, e recusa o que não for endereço IP literal.
     *
     * <p>A coluna é {@code INET}: texto qualquer não entra. Validar aqui é o
     * que impede um cabeçalho torto de derrubar a gravação da auditoria bem no
     * momento em que ela mais importa.
     */
    private static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String endereco = bruto.trim();

        if (endereco.startsWith("[")) {                       // [::1]:8080
            int fecha = endereco.indexOf(']');
            if (fecha < 0) {
                return null;
            }
            endereco = endereco.substring(1, fecha);
        } else if (endereco.indexOf(':') == endereco.lastIndexOf(':')
                && endereco.indexOf(':') > 0) {               // 1.2.3.4:8080
            endereco = endereco.substring(0, endereco.indexOf(':'));
        }

        if (IPV4.matcher(endereco).matches()) {
            for (String octeto : endereco.split("\\.")) {
                if (Integer.parseInt(octeto) > 255) {
                    return null;
                }
            }
            return endereco;
        }
        return IPV6.matcher(endereco).matches() && endereco.contains(":") ? endereco : null;
    }

    private static List<IpAddressMatcher> compilar(List<String> faixas) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String faixa : faixas) {
            if (faixa == null || faixa.isBlank()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(faixa.trim()));
            } catch (IllegalArgumentException e) {
                // Não derruba a aplicação: uma faixa torta na configuração vira
                // "não confio nela", que é o lado seguro do erro.
                log.error("audit.proxy_invalido faixa={} — será ignorada", faixa);
            }
        }
        return List.copyOf(matchers);
    }
}
