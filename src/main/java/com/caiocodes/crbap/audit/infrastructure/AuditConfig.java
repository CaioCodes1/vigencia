package com.caiocodes.crbap.audit.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades do módulo de auditoria. */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {
}
