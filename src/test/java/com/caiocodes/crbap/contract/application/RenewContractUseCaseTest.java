package com.caiocodes.crbap.contract.application;

import com.caiocodes.crbap.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.crbap.contract.domain.Contract;
import com.caiocodes.crbap.contract.domain.ContractId;
import com.caiocodes.crbap.contract.application.port.ContractBillingPort;
import com.caiocodes.crbap.contract.domain.ContractRepository;
import com.caiocodes.crbap.contract.domain.NewContract;
import com.caiocodes.crbap.shared.application.BusinessMetrics;
import com.caiocodes.crbap.shared.application.DomainEventRecorder;
import com.caiocodes.crbap.shared.domain.BillingCycle;
import com.caiocodes.crbap.shared.domain.DateRange;
import com.caiocodes.crbap.shared.domain.Money;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewContractUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-03T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();

    @Mock private ContractRepository contracts;
    @Mock private ContractFinder finder;
    @Mock private ContractBillingPort billings;
    @Mock private DomainEventRecorder events;
    @Mock private BusinessMetrics metrics;

    private RenewContractUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
        useCase = new RenewContractUseCase(contracts, finder, billings, events, metrics, relogio);
    }

    @Test
    @DisplayName("repetir a mesma Idempotency-Key devolve o contrato já criado, sem criar outro")
    void deve_ser_idempotente() {
        Contract jaCriado = umContratoAtivo();
        when(contracts.findByIdempotencyKey("chave-1")).thenReturn(Optional.of(jaCriado));

        RenewContractUseCase.RenewalResult resultado = useCase.execute(
                new ContractCommands.RenewContract(ContractId.newId(),
                        LocalDate.of(2028, 9, 30), null, null, "chave-1"));

        assertThat(resultado.repeated()).isTrue();
        assertThat(resultado.contract().id()).isEqualTo(jaCriado.id().value());
        // O ponto do teste: nada foi gravado na segunda chamada.
        verify(contracts, never()).save(any());
        verify(contracts, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("renova gravando os DOIS agregados — o anterior e o sucessor")
    void deve_gravar_os_dois_contratos() {
        Contract atual = umContratoAtivo();
        when(contracts.findByIdempotencyKey("chave-2")).thenReturn(Optional.empty());
        when(finder.requireForUpdate(atual.id())).thenReturn(atual);
        when(finder.clientRefOf(atual)).thenReturn(clienteAtivo(true));
        when(contracts.save(any())).thenAnswer(i -> i.getArgument(0));

        RenewContractUseCase.RenewalResult resultado = useCase.execute(
                new ContractCommands.RenewContract(atual.id(), LocalDate.of(2028, 9, 30),
                        new BigDecimal("26400.00"), "BRL", "chave-2"));

        assertThat(resultado.repeated()).isFalse();
        assertThat(resultado.contract().previousContractId()).isEqualTo(atual.id().value());
        // Meia renovação seria pior que nenhuma: os dois têm que ser gravados.
        verify(contracts, times(2)).save(any());
        verify(events, times(2)).record(any());
    }

    @Test
    @DisplayName("o início do novo período é o dia seguinte ao fim do anterior — sem buraco")
    void deve_encadear_sem_buraco() {
        Contract atual = umContratoAtivo();
        when(contracts.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(finder.requireForUpdate(atual.id())).thenReturn(atual);
        when(finder.clientRefOf(atual)).thenReturn(clienteAtivo(true));
        when(contracts.save(any())).thenAnswer(i -> i.getArgument(0));

        RenewContractUseCase.RenewalResult resultado = useCase.execute(
                new ContractCommands.RenewContract(atual.id(), LocalDate.of(2028, 9, 30),
                        null, null, "chave-3"));

        assertThat(resultado.contract().startDate())
                .isEqualTo(atual.period().end().plusDays(1));
    }

    @Test
    @DisplayName("omitir o valor mantém o do contrato anterior")
    void deve_manter_o_valor_quando_omitido() {
        Contract atual = umContratoAtivo();
        when(contracts.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(finder.requireForUpdate(atual.id())).thenReturn(atual);
        when(finder.clientRefOf(atual)).thenReturn(clienteAtivo(true));
        when(contracts.save(any())).thenAnswer(i -> i.getArgument(0));

        RenewContractUseCase.RenewalResult resultado = useCase.execute(
                new ContractCommands.RenewContract(atual.id(), LocalDate.of(2028, 9, 30),
                        null, null, "chave-4"));

        assertThat(resultado.contract().value().amount())
                .isEqualByComparingTo(new BigDecimal("24000.00"));
    }

    @Test
    @DisplayName("cliente inativo não renova — senão a renovação automática cobra quem saiu")
    void nao_deve_renovar_cliente_inativo() {
        Contract atual = umContratoAtivo();
        when(contracts.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(finder.requireForUpdate(atual.id())).thenReturn(atual);
        when(finder.clientRefOf(atual)).thenReturn(clienteAtivo(false));

        assertThatThrownBy(() -> useCase.execute(new ContractCommands.RenewContract(
                atual.id(), LocalDate.of(2028, 9, 30), null, null, "chave-5")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cliente inativo");

        verify(contracts, never()).save(any());
    }

    private static ClientRef clienteAtivo(boolean ativo) {
        return new ClientRef(CLIENTE, "Acme Comércio Ltda", ativo, null,
                "financeiro@acme.com", null);
    }

    private static Contract umContratoAtivo() {
        Contract contrato = Contract.create(new NewContract(CLIENTE, "CT-2026-0042",
                "Licença Enterprise", null,
                DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)),
                Money.of("24000.00", "BRL"), BillingCycle.MONTHLY, 10, 5, true,
                UUID.randomUUID()));
        contrato.activate(AGORA);
        contrato.pullEvents();
        return contrato;
    }
}
