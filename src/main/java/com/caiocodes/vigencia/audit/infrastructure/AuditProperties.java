package com.caiocodes.vigencia.audit.infrastructure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da auditoria.
 *
 * <p>A lista de proxies vem vazia de propósito: sem proxy declarado, o
 * {@link ClientIpResolver} ignora {@code X-Forwarded-For} por completo. O
 * padrão inseguro seria confiar no cabeçalho e pedir para desligar depois.
 */
@ConfigurationProperties(prefix = "vigencia.audit")
public record AuditProperties(List<String> trustedProxies) {

    public AuditProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
