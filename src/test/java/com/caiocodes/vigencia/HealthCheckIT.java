package com.caiocodes.vigencia;

import com.caiocodes.vigencia.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O teste que fecha a fase 1: o contexto sobe, o Flyway aplica as migrações
 * contra um Postgres real e a aplicação se declara pronta.
 */
class HealthCheckIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("a aplicação sobe e responde UP")
    void health_deve_responder_up() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("as probes de liveness e readiness respondem separadamente")
    void probes_devem_responder() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("toda resposta volta com X-Trace-Id")
    void deve_devolver_trace_id() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("sem autenticação, rota inexistente devolve 401 — deny by default")
    void rota_desconhecida_sem_token_deve_devolver_401() throws Exception {
        // Não é 404 de propósito: responder 404 para quem nem se identificou
        // permitiria mapear quais rotas existem sem nenhuma credencial.
        mockMvc.perform(get("/api/v1/rota-que-nao-existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("autenticado, rota inexistente devolve 404 no formato padrão")
    void rota_desconhecida_deve_devolver_404_padronizado() throws Exception {
        mockMvc.perform(get("/api/v1/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
