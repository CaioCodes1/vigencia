package com.caiocodes.vigencia.client;

import com.caiocodes.vigencia.support.AbstractIamIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fase 3 de ponta a ponta: cadastro, escopo de carteira e criptografia. */
class ClientIT extends AbstractIamIntegrationTest {

    private static final String SENHA = "senha-bem-comprida-123";
    private static final String CNPJ = "11222333000181";

    // =================================================================
    // Cadastro
    // =================================================================

    @Test
    @DisplayName("cadastra e devolve 201 com o documento mascarado")
    void deve_cadastrar_cliente() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");

        mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s",
                                 "legalName": "Acme Comércio Ltda",
                                 "tradeName": "Acme",
                                 "email": "financeiro@acme.com",
                                 "contacts": [{"name": "Maria", "email": "maria@acme.com",
                                               "primary": true}]}
                                """.formatted(CNPJ)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.document").value("**.***.***/0001-**"))
                .andExpect(jsonPath("$.contacts[0].primary").value(true));
    }

    @Test
    @DisplayName("o documento fica CIFRADO na coluna — não é texto puro no banco")
    void documento_deve_estar_cifrado_em_repouso() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        criarCliente("gestor@empresa.com", CNPJ, "Acme Comércio Ltda");

        byte[] cifrado = jdbc.queryForObject(
                "SELECT document_enc FROM clients LIMIT 1", byte[].class);
        String comoTexto = new String(cifrado, StandardCharsets.ISO_8859_1);

        assertThat(comoTexto)
                .as("um dump do banco sem a chave não pode expor o documento")
                .doesNotContain(CNPJ)
                .doesNotContain("11.222.333");
        // IV (12 bytes) + ciphertext + tag (16 bytes): sempre maior que o texto.
        assertThat(cifrado.length).isGreaterThan(CNPJ.length());
    }

    @Test
    @DisplayName("o índice cego permite achar por documento sem decifrar a tabela")
    void deve_recusar_documento_duplicado_entre_ativos() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        criarCliente("gestor@empresa.com", CNPJ, "Acme Um");

        mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCliente(CNPJ, "Acme Dois")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    @DisplayName("CPF/CNPJ inválido é recusado com 422, não chega ao banco")
    void deve_recusar_documento_invalido() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");

        mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCliente("11222333000180", "Acme")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    // =================================================================
    // Escopo de carteira (IDOR)
    // =================================================================

    @Test
    @DisplayName("vendedor NÃO enxerga cliente de outra carteira — e recebe 404, não 403")
    void vendedor_nao_deve_ver_cliente_alheio() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        dadoUmUsuario("vendedor@empresa.com", SENHA, "SALES");
        String clienteDoGestor = criarCliente("gestor@empresa.com", CNPJ, "Acme do Gestor");

        // 404 e não 403: um 403 confirmaria que este id existe, e daria para
        // enumerar a base inteira trocando o id na URL.
        mockMvc.perform(get("/api/v1/clients/{id}", clienteDoGestor)
                        .header(HttpHeaders.AUTHORIZATION, bearer("vendedor@empresa.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a listagem do vendedor traz só a carteira dele")
    void listagem_deve_respeitar_a_carteira() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        dadoUmUsuario("vendedor@empresa.com", SENHA, "SALES");
        criarCliente("gestor@empresa.com", CNPJ, "Acme do Gestor");
        criarCliente("vendedor@empresa.com", "52998224725", "Cliente do Vendedor");

        mockMvc.perform(get("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("vendedor@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].legalName").value("Cliente do Vendedor"));

        mockMvc.perform(get("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    @DisplayName("mandar accountManagerId de outro não tira o cliente da carteira do vendedor")
    void vendedor_nao_deve_plantar_cliente_em_carteira_alheia() throws Exception {
        var gestor = dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        var vendedor = dadoUmUsuario("vendedor@empresa.com", SENHA, "SALES");

        // O vendedor MANDA o id do gestor no corpo, tentando escolher a carteira:
        mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("vendedor@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"document": "%s", "legalName": "Acme Ltda",
                                 "email": "a@acme.com", "accountManagerId": "%s"}
                                """.formatted(CNPJ, gestor.id().value())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountManagerId")
                        .value(vendedor.id().value().toString()));
    }

    @Test
    @DisplayName("sem client:read a listagem responde 403")
    void sem_permissao_deve_responder_403() throws Exception {
        dadoUmUsuario("orfao@empresa.com", SENHA, null);

        mockMvc.perform(get("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("orfao@empresa.com")))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // Busca
    // =================================================================

    @Test
    @DisplayName("busca por nome ignora acento e maiúscula — é o f_unaccent + trigram")
    void busca_deve_ignorar_acento() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        criarCliente("gestor@empresa.com", CNPJ, "José da Conceição Comércio");

        mockMvc.perform(get("/api/v1/clients").param("search", "jose")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/clients").param("search", "CONCEIÇÃO")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void busca_sem_resultado_deve_devolver_pagina_vazia() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        criarCliente("gestor@empresa.com", CNPJ, "Acme Ltda");

        mockMvc.perform(get("/api/v1/clients").param("search", "inexistente")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // =================================================================
    // Desativação e recadastro
    // =================================================================

    @Test
    @DisplayName("desativar libera o documento para um novo cadastro (índice único parcial)")
    void deve_permitir_recadastro_apos_desativacao() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        String id = criarCliente("gestor@empresa.com", CNPJ, "Acme Antiga");

        mockMvc.perform(delete("/api/v1/clients/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCliente(CNPJ, "Acme Nova")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("reativar com o documento já tomado por outro ativo é recusado")
    void nao_deve_reativar_com_documento_em_uso() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        String antigo = criarCliente("gestor@empresa.com", CNPJ, "Acme Antiga");

        mockMvc.perform(delete("/api/v1/clients/{id}", antigo)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isNoContent());
        criarCliente("gestor@empresa.com", CNPJ, "Acme Nova");

        mockMvc.perform(post("/api/v1/clients/{id}/reactivate", antigo)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    @DisplayName("nada é apagado: o cliente desativado continua consultável")
    void desativado_deve_continuar_existindo() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        String id = criarCliente("gestor@empresa.com", CNPJ, "Acme Ltda");

        mockMvc.perform(delete("/api/v1/clients/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/clients/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.deactivatedAt").isNotEmpty());
    }

    // =================================================================
    // Documento sensível
    // =================================================================

    @Test
    @DisplayName("só quem tem client:read_sensitive vê o documento completo")
    void documento_completo_exige_permissao() throws Exception {
        dadoUmUsuario("gestor@empresa.com", SENHA, "MANAGER");
        dadoUmUsuario("financeiro@empresa.com", SENHA, "FINANCE");
        String id = criarCliente("gestor@empresa.com", CNPJ, "Acme Ltda");

        mockMvc.perform(get("/api/v1/clients/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("gestor@empresa.com")))
                .andExpect(jsonPath("$.document").value("**.***.***/0001-**"));

        mockMvc.perform(get("/api/v1/clients/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer("financeiro@empresa.com")))
                .andExpect(jsonPath("$.document").value("11.222.333/0001-81"));
    }

    // =================================================================
    // Apoio
    // =================================================================

    private String bearer(String email) throws Exception {
        return "Bearer " + tokenDe(email, SENHA);
    }

    private String criarCliente(String comoUsuario, String documento, String razaoSocial)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(comoUsuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCliente(documento, razaoSocial)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText()).toString();
    }

    private static String corpoCliente(String documento, String razaoSocial) {
        return """
                {"document": "%s", "legalName": "%s", "email": "contato@acme.com"}
                """.formatted(documento, razaoSocial);
    }
}
