package com.caiocodes.crbap.notification.application;

import com.caiocodes.crbap.notification.application.ScheduleNotificationUseCase.NotificationRequest;
import com.caiocodes.crbap.notification.application.port.ExpiringContractsPort;
import com.caiocodes.crbap.notification.application.port.ExpiringContractsPort.ContractNotice;
import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Como testar "30 dias antes" sem esperar 30 dias.
 *
 * <p>Este é o retorno do {@code Clock} injetado desde a fase 1. Com
 * {@code LocalDate.now()} espalhado pelo código, este teste seria impossível —
 * só restaria mudar o relógio da máquina ou esperar um mês.
 */
@ExtendWith(MockitoExtension.class)
class ScanExpiringContractsUseCaseTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 1);
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID CONTRATO = UUID.randomUUID();

    @Mock private ExpiringContractsPort contracts;
    @Mock private ScheduleNotificationUseCase schedule;

    private ScanExpiringContractsUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC);
        useCase = new ScanExpiringContractsUseCase(contracts, schedule, relogio);
        // lenient porque um dos testes prova justamente que NADA é agendado.
        lenient().when(schedule.execute(any())).thenReturn(List.of(NotificationId.newId()));
    }

    @Test
    @DisplayName("agenda o aviso D-30 para o contrato que vence em exatamente 30 dias")
    void deve_agendar_aviso_de_30_dias() {
        // 01/09 + 30 = 01/10. É o dia exato que o job procura.
        when(contracts.findActiveEndingOn(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(umContrato(LocalDate.of(2026, 10, 1))));

        useCase.execute();

        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(schedule).execute(captor.capture());

        NotificationRequest pedido = captor.getValue();
        assertThat(pedido.type()).isEqualTo(NotificationType.CONTRACT_EXPIRING);
        assertThat(pedido.daysOffset()).isEqualTo(30);
        assertThat(pedido.contractId()).isEqualTo(CONTRATO);
        assertThat(pedido.subject()).isEqualTo("Contrato CT-2026-0042 vence em 30 dias");
    }

    @Test
    @DisplayName("a janela é EXATA: contrato que vence em 29 dias não entra em nenhuma")
    void janela_deve_ser_exata() {
        // Nenhuma das quatro janelas casa com 29 dias — e é assim que se evita
        // mandar o mesmo aviso todo dia por trinta dias seguidos.
        useCase.execute();

        verify(contracts).findActiveEndingOn(LocalDate.of(2026, 10, 1));
        verify(contracts).findActiveEndingOn(LocalDate.of(2026, 9, 16));
        verify(contracts).findActiveEndingOn(LocalDate.of(2026, 9, 8));
        verify(contracts).findActiveEndingOn(LocalDate.of(2026, 9, 2));
        verify(contracts, never()).findActiveEndingOn(LocalDate.of(2026, 9, 30));
        verify(schedule, never()).execute(any());
    }

    @Test
    @DisplayName("o aviso de D-1 diz 'amanhã', não 'em 1 dias'")
    void aviso_de_um_dia_deve_dizer_amanha() {
        // lenient: o scan percorre D-30 antes de chegar a D-1, e o Mockito
        // estrito acusa a primeira chamada que não casa com o stub.
        lenient().when(contracts.findActiveEndingOn(LocalDate.of(2026, 9, 2)))
                .thenReturn(List.of(umContrato(LocalDate.of(2026, 9, 2))));

        useCase.execute();

        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(schedule).execute(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Contrato CT-2026-0042 vence amanhã");
    }

    @Test
    @DisplayName("contrato vencido ontem gera o aviso D+1, com janela negativa")
    void deve_agendar_aviso_pos_vencimento() {
        when(contracts.findExpiredEndedOn(LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(umContrato(LocalDate.of(2026, 8, 31))));

        useCase.execute();

        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(schedule).execute(captor.capture());

        NotificationRequest pedido = captor.getValue();
        assertThat(pedido.type()).isEqualTo(NotificationType.CONTRACT_EXPIRED);
        // Negativa para não colidir com a janela de mesmo número antes do
        // vencimento: D-1 e D+1 são avisos diferentes do mesmo contrato.
        assertThat(pedido.daysOffset()).isEqualTo(-1);
        assertThat(pedido.subject()).isEqualTo("Contrato CT-2026-0042 venceu há 1 dia(s)");
    }

    @Test
    @DisplayName("o payload leva o conteúdo já formatado, congelado no agendamento")
    void payload_deve_vir_formatado() {
        when(contracts.findActiveEndingOn(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(umContrato(LocalDate.of(2026, 10, 1))));

        useCase.execute();

        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(schedule).execute(captor.capture());

        assertThat(captor.getValue().payload())
                .containsEntry("contractNumber", "CT-2026-0042")
                .containsEntry("endDateFormatted", "01/10/2026")
                .containsEntry("daysOffset", 30)
                .containsEntry("autoRenew", false);
    }

    @Test
    @DisplayName("o aviso vai também para o gestor da conta")
    void deve_avisar_o_gestor() {
        when(contracts.findActiveEndingOn(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(umContrato(LocalDate.of(2026, 10, 1))));

        useCase.execute();

        ArgumentCaptor<NotificationRequest> captor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(schedule).execute(captor.capture());
        assertThat(captor.getValue().notifyAccountManager()).isTrue();
    }

    private static ContractNotice umContrato(LocalDate fim) {
        return new ContractNotice(CONTRATO, CLIENTE, "CT-2026-0042", "Licença Enterprise",
                fim.minusYears(1).plusDays(1), fim, new BigDecimal("24000.00"), "BRL", false);
    }
}
