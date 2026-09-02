package com.caiocodes.crbap.client.application;

import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientRepository;
import com.caiocodes.crbap.shared.application.CurrentUser;
import com.caiocodes.crbap.shared.domain.Document;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateClientUseCaseTest {

    private static final String CNPJ = "11222333000181";

    @Mock private ClientRepository clients;
    @Mock private CurrentUser currentUser;

    private CreateClientUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateClientUseCase(clients, currentUser);
    }

    @Test
    @DisplayName("documento já usado por um cliente ativo é recusado antes de tocar no banco")
    void deve_recusar_documento_duplicado() {
        when(clients.existsActiveWithDocument(any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(comando(null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Já existe um cliente ativo");

        verify(clients, never()).save(any());
    }

    @Test
    @DisplayName("vendedor não escolhe a carteira: o cliente nasce na dele")
    void vendedor_deve_ficar_com_o_proprio_cliente() {
        UUID vendedor = UUID.randomUUID();
        UUID outroVendedor = UUID.randomUUID();
        when(clients.existsActiveWithDocument(any())).thenReturn(false);
        when(currentUser.hasPermission("client:read_all")).thenReturn(false);
        when(currentUser.id()).thenReturn(vendedor);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));

        // Ele MANDA o id de outro vendedor no corpo da requisição:
        useCase.execute(comando(outroVendedor));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(captor.capture());
        assertThat(captor.getValue().accountManagerId())
                .as("o id enviado no corpo é ignorado para quem não vê todas as carteiras")
                .isEqualTo(vendedor);
    }

    @Test
    @DisplayName("gestor pode atribuir o cliente à carteira de outra pessoa")
    void gestor_deve_poder_escolher_a_carteira() {
        UUID vendedor = UUID.randomUUID();
        when(clients.existsActiveWithDocument(any())).thenReturn(false);
        when(currentUser.hasPermission("client:read_all")).thenReturn(true);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(comando(vendedor));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(captor.capture());
        assertThat(captor.getValue().accountManagerId()).isEqualTo(vendedor);
    }

    @Test
    @DisplayName("sem client:read_sensitive, o documento volta mascarado")
    void deve_mascarar_o_documento_por_padrao() {
        when(clients.existsActiveWithDocument(any())).thenReturn(false);
        when(currentUser.hasPermission("client:read_all")).thenReturn(true);
        when(currentUser.hasPermission("client:read_sensitive")).thenReturn(false);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));

        ClientDetail detail = useCase.execute(comando(null));

        assertThat(detail.document()).isEqualTo(Document.of(CNPJ).masked())
                .doesNotContain(CNPJ);
    }

    @Test
    void com_a_permissao_o_documento_volta_completo() {
        when(clients.existsActiveWithDocument(any())).thenReturn(false);
        when(currentUser.hasPermission("client:read_all")).thenReturn(true);
        when(currentUser.hasPermission("client:read_sensitive")).thenReturn(true);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(useCase.execute(comando(null)).document())
                .isEqualTo(Document.of(CNPJ).formatted());
    }

    @Test
    @DisplayName("o contato informado no cadastro entra como principal")
    void deve_criar_com_contato() {
        when(clients.existsActiveWithDocument(any())).thenReturn(false);
        when(currentUser.hasPermission("client:read_all")).thenReturn(true);
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));

        ClientDetail detail = useCase.execute(new ClientCommands.CreateClient(
                CNPJ, "Acme Ltda", "Acme", "financeiro@acme.com", null, null, null,
                List.of(new ClientCommands.NewContact("Maria", "maria@acme.com", null,
                        "Financeiro", false))));

        assertThat(detail.contacts()).hasSize(1);
        assertThat(detail.contacts().get(0).primary()).isTrue();
    }

    private static ClientCommands.CreateClient comando(UUID accountManagerId) {
        return new ClientCommands.CreateClient(CNPJ, "Acme Comércio Ltda", "Acme",
                "financeiro@acme.com", null, null, accountManagerId, List.of());
    }
}
