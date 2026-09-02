package com.caiocodes.crbap.client.domain;

import com.caiocodes.crbap.shared.domain.Document;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import com.caiocodes.crbap.shared.domain.exception.IllegalStateTransitionException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");
    private static final String CNPJ = "11.222.333/0001-81";

    @Nested
    @DisplayName("contatos")
    class Contatos {

        @Test
        @DisplayName("o primeiro contato vira principal mesmo sem pedir")
        void primeiro_contato_deve_ser_principal() {
            Client client = umCliente();

            client.addContact("Maria", "maria@acme.com", null, "Financeiro", false);

            assertThat(client.primaryContact()).isPresent();
            assertThat(client.primaryContact().orElseThrow().name()).isEqualTo("Maria");
        }

        @Test
        @DisplayName("marcar um novo principal desmarca o anterior — invariante do agregado")
        void deve_existir_no_maximo_um_principal() {
            Client client = umCliente();
            client.addContact("Maria", "maria@acme.com", null, null, true);

            client.addContact("João", "joao@acme.com", null, null, true);

            assertThat(client.contacts()).hasSize(2);
            assertThat(client.contacts().stream().filter(ClientContact::isPrimary).count())
                    .isEqualTo(1);
            assertThat(client.primaryContact().orElseThrow().name()).isEqualTo("João");
        }

        @Test
        @DisplayName("não dá para remover o principal deixando o cliente sem ninguém marcado")
        void nao_deve_remover_o_principal_havendo_outros() {
            Client client = umCliente();
            client.addContact("Maria", "maria@acme.com", null, null, true);
            client.addContact("João", "joao@acme.com", null, null, false);
            UUID principal = client.primaryContact().orElseThrow().id();

            assertThatThrownBy(() -> client.removeContact(principal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("outro contato principal");
        }

        @Test
        void deve_remover_o_ultimo_contato_mesmo_sendo_principal() {
            Client client = umCliente();
            client.addContact("Maria", "maria@acme.com", null, null, true);

            client.removeContact(client.primaryContact().orElseThrow().id());

            assertThat(client.contacts()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"sem-arroba", "@sem-usuario.com", "a b@c.com"})
        void deve_recusar_email_de_contato_invalido(String email) {
            assertThatThrownBy(() -> umCliente().addContact("Maria", email, null, null, true))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("ciclo de vida")
    class CicloDeVida {

        @Test
        void deve_desativar_e_reativar() {
            Client client = umCliente();

            client.deactivate(AGORA);
            assertThat(client.status()).isEqualTo(ClientStatus.INACTIVE);
            assertThat(client.deactivatedAt()).isEqualTo(AGORA);
            assertThat(client.isActive()).isFalse();

            client.reactivate();
            assertThat(client.status()).isEqualTo(ClientStatus.ACTIVE);
            assertThat(client.deactivatedAt()).isNull();
        }

        @Test
        void desativar_duas_vezes_deve_falhar() {
            Client client = umCliente();
            client.deactivate(AGORA);

            assertThatThrownBy(() -> client.deactivate(AGORA))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("cliente inativo não aceita alteração cadastral")
        void inativo_nao_deve_ser_editado() {
            Client client = umCliente();
            client.deactivate(AGORA);

            assertThatThrownBy(() -> client.updateProfile("Outro Nome", null,
                    "novo@acme.com", null, null, null))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(() -> client.addContact("Maria", "maria@acme.com",
                    null, null, true))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        void bloqueado_nao_e_o_mesmo_que_inativo() {
            Client client = umCliente();

            client.block();

            assertThat(client.status()).isEqualTo(ClientStatus.BLOCKED);
            assertThat(client.isActive()).isFalse();
            assertThat(client.deactivatedAt()).isNull();
        }
    }

    @Test
    @DisplayName("atualizar o cadastro NÃO muda o documento — ele é a identidade fiscal")
    void documento_deve_ser_imutavel() {
        Client client = umCliente();
        Document original = client.document();

        client.updateProfile("Acme Nova Razão", "Acme", "novo@acme.com", null, null, "obs");

        assertThat(client.document()).isEqualTo(original);
        assertThat(client.legalName()).isEqualTo("Acme Nova Razão");
    }

    @Test
    void deve_normalizar_email_e_recusar_razao_social_curta() {
        assertThat(Client.create(Document.of(CNPJ), "Acme Ltda", null, "  Contato@ACME.com ",
                null, null, null).email()).isEqualTo("contato@acme.com");

        assertThatThrownBy(() -> Client.create(Document.of(CNPJ), "AB", null, "a@b.com",
                null, null, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("saber a carteira é do agregado, não de um if no controller")
    void deve_responder_de_quem_e_a_carteira() {
        UUID vendedor = UUID.randomUUID();
        Client client = Client.create(Document.of(CNPJ), "Acme Ltda", null, "a@b.com",
                null, null, vendedor);

        assertThat(client.isManagedBy(vendedor)).isTrue();
        assertThat(client.isManagedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("toString nunca imprime o documento completo")
    void to_string_nao_deve_vazar_documento() {
        assertThat(umCliente().toString())
                .doesNotContain("11222333000181")
                .contains("***");
    }

    private static Client umCliente() {
        return Client.create(Document.of(CNPJ), "Acme Comércio Ltda", "Acme",
                "financeiro@acme.com", "+5511999998888", null, UUID.randomUUID());
    }
}
