package com.caiocodes.vigencia.shared.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI. O restante da documentação é gerado do próprio código —
 * documentação escrita à mão diverge do comportamento real em duas semanas.
 *
 * <p>O esquema de segurança (bearer JWT) entra aqui na fase 2, junto com o
 * Spring Security.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vigenciaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Vigência — Renovação de Contratos e Cobrança")
                .version("v1")
                .description("""
                        API de gestão de contratos recorrentes: clientes, contratos,
                        renovações, cobranças, pagamentos, notificações automáticas
                        de vencimento e relatórios gerenciais.""")
                .contact(new Contact().name("Caio Martins").url("https://github.com/CaioCodes1"))
                .license(new License().name("MIT")));
    }
}
