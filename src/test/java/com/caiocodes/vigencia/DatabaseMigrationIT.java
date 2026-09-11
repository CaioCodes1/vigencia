package com.caiocodes.vigencia;

import com.caiocodes.vigencia.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que as migrações fazem o que dizem — em Postgres de verdade.
 *
 * <p>Constraint e índice parcial só são testáveis com o banco real; é
 * exatamente o tipo de regra que um mock esconde e que aparece em produção,
 * sob concorrência.
 */
class DatabaseMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("as tabelas da fase 1 existem")
    void tabelas_devem_existir() {
        var tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tabelas).contains(
                "users", "roles", "permissions", "user_roles", "role_permissions",
                "refresh_tokens", "clients", "client_contacts", "flyway_schema_history");
    }

    @Test
    @DisplayName("f_unaccent é IMMUTABLE e remove acento — é o que sustenta o índice de busca")
    void f_unaccent_deve_funcionar() {
        String semAcento = jdbc.queryForObject("SELECT f_unaccent('José da Conceição')",
                String.class);
        assertThat(semAcento).isEqualTo("Jose da Conceicao");
    }

    @Test
    @DisplayName("o índice único parcial impede dois clientes ATIVOS com o mesmo documento")
    void documento_duplicado_entre_ativos_deve_falhar() {
        byte[] documento = "12345678000199".getBytes(StandardCharsets.UTF_8);
        inserirCliente("Acme Um", documento, "um@acme.com");

        assertThatThrownBy(() -> inserirCliente("Acme Dois", documento, "dois@acme.com"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("mas permite recadastrar o documento depois que o cliente é desativado")
    void documento_deve_ser_reutilizavel_apos_desativacao() {
        byte[] documento = "98765432000188".getBytes(StandardCharsets.UTF_8);
        UUID primeiro = inserirCliente("Beta Um", documento, "um@beta.com");

        jdbc.update("UPDATE clients SET status = 'INACTIVE', deactivated_at = now() WHERE id = ?",
                primeiro);

        assertThatNoException().isThrownBy(
                () -> inserirCliente("Beta Dois", documento, "dois@beta.com"));
    }

    @Test
    @DisplayName("status INACTIVE sem deactivated_at é recusado pelo CHECK")
    void estado_inconsistente_deve_ser_recusado() {
        UUID id = inserirCliente("Gama", "11222333000181".getBytes(StandardCharsets.UTF_8),
                "gama@x.com");

        assertThatThrownBy(() ->
                jdbc.update("UPDATE clients SET status = 'INACTIVE' WHERE id = ?", id))
                .hasMessageContaining("ck_clients_deact");
    }

    @Test
    @DisplayName("só pode haver um contato principal por cliente")
    void dois_contatos_principais_devem_falhar() {
        UUID cliente = inserirCliente("Delta", "11444777000161".getBytes(StandardCharsets.UTF_8),
                "delta@x.com");
        inserirContatoPrincipal(cliente, "Maria", "maria@delta.com");

        assertThatThrownBy(() -> inserirContatoPrincipal(cliente, "João", "joao@delta.com"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private UUID inserirCliente(String nome, byte[] documento, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO clients (id, legal_name, document_enc, document_index,
                                     document_type, email)
                VALUES (?, ?, ?, ?, 'CNPJ', ?)
                """, id, nome, documento, documento, email);
        return id;
    }

    private void inserirContatoPrincipal(UUID clienteId, String nome, String email) {
        jdbc.update("""
                INSERT INTO client_contacts (id, client_id, name, email, is_primary)
                VALUES (?, ?, ?, ?, TRUE)
                """, UUID.randomUUID(), clienteId, nome, email);
    }
}
