package com.caiocodes.vigencia.notification.web;

import com.caiocodes.vigencia.notification.application.GetNotificationUseCase;
import com.caiocodes.vigencia.notification.application.NotificationView;
import com.caiocodes.vigencia.notification.application.ResendNotificationUseCase;
import com.caiocodes.vigencia.notification.domain.NotificationId;
import com.caiocodes.vigencia.notification.domain.NotificationStatus;
import com.caiocodes.vigencia.notification.domain.NotificationType;
import com.caiocodes.vigencia.shared.infrastructure.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O log de envio — a tela que responde <i>"vocês me avisaram?"</i>.
 *
 * <p>É a pergunta que a planilha nunca respondeu. Aqui ela tem resposta com
 * data, hora, destinatário e o conteúdo exato que saiu.
 *
 * <p>Sem escopo de carteira, ao contrário de contratos e cobranças: quem tem
 * {@code notification:read} está investigando um caso específico — normalmente
 * porque um cliente ligou — e precisa achar o registro pelo id do contrato, não
 * navegar na própria lista.
 */
@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final GetNotificationUseCase getNotification;
    private final ResendNotificationUseCase resendNotification;

    @Operation(summary = "Consulta o log de envio de avisos")
    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public PageResponse<NotificationView> search(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(getNotification.search(type, status, clientId, contractId,
                from, to, page, size));
    }

    @Operation(summary = "Detalha um aviso, com o conteúdo que saiu")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('notification:read')")
    public NotificationView byId(@PathVariable UUID id) {
        return getNotification.byId(NotificationId.of(id));
    }

    /**
     * Reenvio manual.
     *
     * <p>Devolve o aviso à fila; quem manda continua sendo o job. Só um aviso
     * em {@code FAILED} pode ser reenviado — reenviar um que já saiu seria
     * mandar o mesmo e-mail duas vezes para o cliente.
     */
    @Operation(summary = "Recoloca na fila um aviso que falhou")
    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAuthority('notification:send')")
    public NotificationView resend(@PathVariable UUID id) {
        return resendNotification.execute(NotificationId.of(id));
    }
}
