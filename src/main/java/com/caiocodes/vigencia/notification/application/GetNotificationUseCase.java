package com.caiocodes.vigencia.notification.application;

import com.caiocodes.vigencia.notification.domain.NotificationId;
import com.caiocodes.vigencia.notification.domain.NotificationRepository;
import com.caiocodes.vigencia.notification.domain.NotificationSearchCriteria;
import com.caiocodes.vigencia.notification.domain.NotificationStatus;
import com.caiocodes.vigencia.notification.domain.NotificationType;
import com.caiocodes.vigencia.shared.domain.PageResult;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A consulta do log de envio (RF-24). */
@Service
@RequiredArgsConstructor
public class GetNotificationUseCase {

    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public NotificationView byId(NotificationId id) {
        return notifications.findById(id)
                .map(NotificationView::from)
                .orElseThrow(() -> new NotFoundException("Notificação", id));
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> search(NotificationType type, NotificationStatus status,
                                               UUID clientId, UUID contractId,
                                               Instant from, Instant until,
                                               int page, int size) {
        return notifications.search(new NotificationSearchCriteria(
                        type, status, clientId, contractId, from, until, page, size))
                .map(NotificationView::from);
    }
}
