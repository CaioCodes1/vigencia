package com.caiocodes.crbap.iam.infrastructure.security;

import com.caiocodes.crbap.shared.infrastructure.web.ApiError;
import com.caiocodes.crbap.shared.infrastructure.web.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * A configuração de segurança.
 *
 * <p>A linha mais importante é a última do bloco de autorização:
 * {@code .anyRequest().authenticated()}. É <b>deny by default</b> — endpoint
 * novo nasce protegido, e liberar exige uma decisão explícita. O contrário
 * (liberar por padrão e proteger o que lembrar) é como começa a maioria dos
 * vazamentos.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF desligado porque a credencial vai no header Authorization,
                // e navegador não preenche header sozinho — logo, não existe a
                // requisição forjada que o CSRF token protege. Se um dia o
                // refresh passar a viajar em cookie HttpOnly, o CSRF volta a
                // valer PARA AQUELA ROTA. Ver ADR-005.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("system:monitor")
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                            .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, AuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                write(response, request.getRequestURI(),
                                        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                        "Autenticação necessária"))
                        .accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    /**
     * 403 sem dizer <b>qual</b> permissão faltou: a lista de permissões do
     * sistema é informação útil para quem estiver sondando a API.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, denied) -> write(response, request.getRequestURI(),
                HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Você não tem permissão para executar esta operação");
    }

    /**
     * CORS com origem explícita. Nunca {@code *} junto de
     * {@code allowCredentials} — o navegador recusa, e com razão.
     */
    @Bean
    @ConditionalOnProperty(name = "crbap.cors.allowed-origins")
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${crbap.cors.allowed-origins}")
            List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type",
                "Idempotency-Key", "X-Trace-Id"));
        configuration.setExposedHeaders(List.of("X-Trace-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void write(jakarta.servlet.http.HttpServletResponse response, String path,
                       HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiError.of(status.value(), code, message,
                path, TraceIdFilter.currentTraceId()));
    }
}
