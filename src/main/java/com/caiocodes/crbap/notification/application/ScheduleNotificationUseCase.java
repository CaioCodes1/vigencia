package com.caiocodes.crbap.notification.application;

import com.caiocodes.crbap.notification.domain.NewNotification;
import com.caiocodes.crbap.notification.domain.Notification;
import com.caiocodes.crbap.notification.domain.NotificationChannel;
import com.caiocodes.crbap.notification.domain.NotificationId;
import com.caiocodes.crbap.notification.domain.NotificationRepository;
import com.caiocodes.crbap.notification.domain.NotificationType;
import com.caiocodes.crbap.shared.application.ClientDirectory;
import com.caiocodes.crbap.shared.application.ClientDirectory.ClientRef;
import com.caiocodes.crbap.shared.application.UserDirectory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda um aviso para todos os destinatários que ele deve ter.
 *
 * <p>Um "aviso" do ponto de vista do negócio vira <b>N linhas</b>: uma para o
 * contato do cliente e outra para o gestor da conta. Elas são registros
 * distintos de propósito — o e-mail do cliente pode falhar e o do gestor sair,
 * e o log de envio precisa contar essa história com precisão.
 *
 * <p>Nada aqui verifica se o aviso já existe. Quem garante é o índice único
 * {@code (contract_id, type, days_offset, recipient)}: um {@code SELECT} antes
 * do {@code INSERT} não sobrevive a duas instâncias rodando o job ao mesmo
 * tempo, e é exatamente esse o cenário. O repositório traduz a violação em
 * "não agendei, já existia".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleNotificationUseCase {

    private final NotificationRepository notifications;
    private final ClientDirectory clients;
    private final UserDirectory users;
    private final Clock clock;

    /**
     * @return os avisos efetivamente criados — vazio quando todos já existiam
     */
    @Transactional
    public List<NotificationId> execute(NotificationRequest request) {
        ClientRef client = clients.findRef(request.clientId()).orElse(null);
        if (client == null) {
            log.warn("notification.skip motivo=cliente_inexistente clientId={}",
                    request.clientId());
            return List.of();
        }

        Set<String> destinatarios = recipients(client, request.notifyAccountManager());
        if (destinatarios.isEmpty()) {
            // Acontece de verdade: cliente sem contato e sem e-mail no cadastro.
            // É um problema de dados, não de código — por isso WARN e segue.
            log.warn("notification.skip motivo=sem_destinatario clientId={} tipo={}",
                    request.clientId(), request.type());
            return List.of();
        }

        List<NotificationId> criados = new ArrayList<>();
        for (String destinatario : destinatarios) {
            Notification aviso = Notification.schedule(new NewNotification(
                    request.type(),
                    NotificationChannel.EMAIL,
                    client.id(),
                    request.contractId(),
                    request.billingId(),
                    request.daysOffset(),
                    destinatario,
                    request.subject(),
                    request.payload(),
                    clock.instant()));

            notifications.scheduleIfAbsent(aviso)
                    .ifPresentOrElse(
                            salvo -> criados.add(salvo.id()),
                            () -> log.debug("notification.duplicada tipo={} janela={} para={}",
                                    request.type(), request.daysOffset(), destinatario));
        }

        if (!criados.isEmpty()) {
            log.info("notification.scheduled tipo={} janela={} destinatarios={}",
                    request.type(), request.daysOffset(), criados.size());
        }
        return criados;
    }

    /**
     * {@code LinkedHashSet} para não mandar duas vezes ao mesmo endereço
     * mantendo a ordem: o cliente primeiro, o gestor depois.
     *
     * <p>O caso que o {@code Set} resolve é real — em cliente pequeno, o contato
     * cadastrado às vezes é o próprio gestor da conta.
     */
    private Set<String> recipients(ClientRef client, boolean notifyAccountManager) {
        Set<String> destinatarios = new LinkedHashSet<>();
        if (client.notificationEmail() != null) {
            destinatarios.add(client.notificationEmail().toLowerCase());
        }
        if (notifyAccountManager && client.accountManagerId() != null) {
            users.findRef(client.accountManagerId())
                    .filter(UserDirectory.UserRef::active)
                    .map(UserDirectory.UserRef::email)
                    .ifPresent(email -> destinatarios.add(email.toLowerCase()));
        }
        return destinatarios;
    }

    /**
     * O pedido de aviso.
     *
     * @param notifyAccountManager manda cópia para o gestor da conta. Falso nos
     *                             avisos que são só do cliente (recibo de
     *                             pagamento), verdadeiro nos que a empresa
     *                             precisa acompanhar (vencimento, inadimplência)
     */
    public record NotificationRequest(
            NotificationType type,
            UUID clientId,
            UUID contractId,
            UUID billingId,
            Integer daysOffset,
            String subject,
            Map<String, Object> payload,
            boolean notifyAccountManager) {
    }
}
