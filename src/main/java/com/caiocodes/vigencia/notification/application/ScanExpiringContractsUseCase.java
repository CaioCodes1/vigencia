package com.caiocodes.vigencia.notification.application;

import com.caiocodes.vigencia.notification.application.ScheduleNotificationUseCase.NotificationRequest;
import com.caiocodes.vigencia.notification.application.port.ExpiringContractsPort;
import com.caiocodes.vigencia.notification.application.port.ExpiringContractsPort.ContractNotice;
import com.caiocodes.vigencia.notification.domain.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A régua de avisos de vencimento (RF-13 e RF-14).
 *
 * <p><b>É aqui que o produto passa a existir.</b> Tudo antes disto — contrato,
 * cobrança, cadastro — a planilha também fazia, mal. O que a planilha nunca fez
 * foi avisar sozinha.
 *
 * <p>As janelas são <b>exatas</b>, não "menor ou igual": o contrato entra na
 * lista de D-30 no dia em que faltam exatamente 30 dias. Usar {@code <= 30}
 * mandaria o mesmo aviso todo dia por trinta dias.
 *
 * <p>Rodar duas vezes no mesmo dia não duplica nada — quem garante é o índice
 * único por (contrato, tipo, janela, destinatário), não este código. Ver
 * {@code ScheduleNotificationUseCase}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanExpiringContractsUseCase {

    /** Antes de vencer. Quatro toques, cada um com mais urgência que o anterior. */
    static final List<Integer> JANELAS_ANTES = List.of(30, 15, 7, 1);

    /**
     * Depois de vencer. Só dois: a partir daí o assunto deixa de ser aviso
     * automático e vira ligação do comercial.
     */
    static final List<Integer> JANELAS_DEPOIS = List.of(1, 7);

    private final ExpiringContractsPort contracts;
    private final ScheduleNotificationUseCase schedule;
    private final Clock clock;

    /** @return quantos avisos foram agendados nesta rodada */
    public int execute() {
        LocalDate hoje = LocalDate.now(clock);
        int agendados = 0;

        for (int dias : JANELAS_ANTES) {
            for (ContractNotice contrato : contracts.findActiveEndingOn(hoje.plusDays(dias))) {
                agendados += agendar(contrato, NotificationType.CONTRACT_EXPIRING, dias, hoje);
            }
        }

        for (int dias : JANELAS_DEPOIS) {
            for (ContractNotice contrato : contracts.findExpiredEndedOn(hoje.minusDays(dias))) {
                agendados += agendar(contrato, NotificationType.CONTRACT_EXPIRED, -dias, hoje);
            }
        }

        log.info("notification.expiring_scan data={} avisos={}", hoje, agendados);
        return agendados;
    }

    private int agendar(ContractNotice contrato, NotificationType tipo, int janela,
                        LocalDate hoje) {
        return schedule.execute(new NotificationRequest(
                tipo,
                contrato.clientId(),
                contrato.contractId(),
                null,
                janela,
                assunto(contrato, tipo, janela),
                payload(contrato, janela, hoje),
                true)).size();
    }

    /**
     * O assunto é montado aqui, e não no template, porque ele é gravado na
     * linha da notificação: é o que aparece na tela "vocês me avisaram?" sem
     * precisar renderizar nada de novo.
     */
    private static String assunto(ContractNotice contrato, NotificationType tipo, int janela) {
        if (tipo == NotificationType.CONTRACT_EXPIRED) {
            return "Contrato %s venceu há %d dia(s)".formatted(contrato.number(), -janela);
        }
        if (janela == 1) {
            return "Contrato %s vence amanhã".formatted(contrato.number());
        }
        return "Contrato %s vence em %d dias".formatted(contrato.number(), janela);
    }

    /**
     * O que o template vai renderizar, congelado no momento do agendamento.
     *
     * <p>Guardar o payload em vez de recarregar o contrato na hora do envio é o
     * que permite reconstruir, meses depois, exatamente o e-mail que saiu —
     * mesmo que o contrato tenha sido renovado por outro valor no meio do
     * caminho.
     */
    private static Map<String, Object> payload(ContractNotice contrato, int janela,
                                               LocalDate hoje) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("contractNumber", contrato.number());
        dados.put("contractTitle", contrato.title());
        dados.put("startDate", contrato.startDate().toString());
        dados.put("endDate", contrato.endDate().toString());
        dados.put("startDateFormatted", NotificationFormats.date(contrato.startDate()));
        dados.put("endDateFormatted", NotificationFormats.date(contrato.endDate()));
        dados.put("amount", contrato.amount().toPlainString());
        dados.put("currency", contrato.currency());
        dados.put("amountFormatted",
                NotificationFormats.money(contrato.amount(), contrato.currency()));
        dados.put("daysOffset", janela);
        dados.put("autoRenew", contrato.autoRenew());
        dados.put("referenceDate", hoje.toString());
        return dados;
    }
}
