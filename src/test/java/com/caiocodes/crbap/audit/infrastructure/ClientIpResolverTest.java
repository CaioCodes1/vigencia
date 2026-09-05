package com.caiocodes.crbap.audit.infrastructure;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que impede a trilha de auditoria de virar ficção.
 *
 * <p>Se o {@code X-Forwarded-For} for aceito sem critério, todo IP gravado é o
 * IP que o cliente quis escrever — e a auditoria fica pior do que se não
 * tivesse campo nenhum, porque parece prova.
 */
class ClientIpResolverTest {

    private static final String PROXY = "10.0.0.7";

    @Test
    @DisplayName("sem proxy configurado, o cabeçalho é ignorado")
    void deve_ignorar_cabecalho_sem_proxy_declarado() {
        ClientIpResolver resolver = comProxies();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("200.150.10.22");
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        assertThat(resolver.resolve(request))
                .as("é o padrão da aplicação: sem proxy, o cabeçalho não vale nada")
                .isEqualTo("200.150.10.22");
    }

    @Test
    @DisplayName("com proxy confiável, vale o endereço que ele atesta")
    void deve_aceitar_cabecalho_vindo_do_proxy() {
        ClientIpResolver resolver = comProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", "200.150.10.22");

        assertThat(resolver.resolve(request)).isEqualTo("200.150.10.22");
    }

    @Test
    @DisplayName("a lista é lida da direita para a esquerda — o começo dela é do cliente")
    void deve_ler_da_direita_para_a_esquerda() {
        ClientIpResolver resolver = comProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        // O cliente mandou "1.2.3.4" pronto; os dois últimos foram anexados
        // pela nossa infraestrutura. Ler o primeiro seria ler a mentira.
        request.addHeader("X-Forwarded-For", "1.2.3.4, 200.150.10.22, 10.0.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("200.150.10.22");
    }

    @Test
    @DisplayName("requisição interna, só com proxies conhecidos na lista, fica com o peer")
    void deve_devolver_o_peer_quando_tudo_e_interno() {
        ClientIpResolver resolver = comProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", "10.0.0.9, 10.0.0.3");

        assertThat(resolver.resolve(request)).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("lista malformada derruba a confiança na lista inteira")
    void deve_recusar_cabecalho_torto() {
        ClientIpResolver resolver = comProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", "nao-e-um-ip, 10.0.0.9");

        assertThat(resolver.resolve(request))
                .as("a coluna é INET: texto qualquer derrubaria a gravação")
                .isEqualTo(PROXY);
    }

    @Test
    @DisplayName("porta e colchetes são removidos")
    void deve_normalizar_porta_e_colchetes() {
        ClientIpResolver resolver = comProxies("10.0.0.0/8");

        MockHttpServletRequest comPorta = new MockHttpServletRequest();
        comPorta.setRemoteAddr(PROXY);
        comPorta.addHeader("X-Forwarded-For", "200.150.10.22:51344");
        assertThat(resolver.resolve(comPorta)).isEqualTo("200.150.10.22");

        MockHttpServletRequest ipv6 = new MockHttpServletRequest();
        ipv6.setRemoteAddr(PROXY);
        ipv6.addHeader("X-Forwarded-For", "[2001:db8::1]:443");
        assertThat(resolver.resolve(ipv6)).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("faixa inválida na configuração é ignorada, não derruba a aplicação")
    void deve_ignorar_faixa_invalida() {
        ClientIpResolver resolver = comProxies("isto-nao-e-cidr");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        assertThat(resolver.resolve(request))
                .as("faixa torta vira 'não confio nela', que é o lado seguro")
                .isEqualTo(PROXY);
    }

    private static ClientIpResolver comProxies(String... faixas) {
        return new ClientIpResolver(new AuditProperties(List.of(faixas)));
    }
}
