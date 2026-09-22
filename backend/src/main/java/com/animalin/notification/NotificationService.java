package com.animalin.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.animalin.messaging.MessageRepository;
import com.animalin.security.TenantContext;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> STAFF_ROLES = Set.of("TENANT_OWNER", "TENANT_ADMIN", "VETERINARIAN", "RECEPTIONIST");

    private final AppNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final DeviceService deviceService;
    private final FcmPushService fcmPushService;

    public NotificationService(AppNotificationRepository notificationRepository, UserRepository userRepository,
                               MessageRepository messageRepository, DeviceService deviceService, FcmPushService fcmPushService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.deviceService = deviceService;
        this.fcmPushService = fcmPushService;
    }

    @Transactional
    public void notifyUser(Long tenantId, Long userId, String type, String titleEs, String titleEn, String bodyEs, String bodyEn,
                           String entityType, Long entityId) {
        AppNotification notification = new AppNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitleEs(titleEs);
        notification.setTitleEn(titleEn);
        notification.setBodyEs(bodyEs);
        notification.setBodyEn(bodyEn);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notificationRepository.save(notification);
        log.debug("In-app notification stored userId={} type={}", userId, type);
    }

    @Transactional
    public void notifyChatMessage(Long tenantId, Long recipientUserId, Long senderUserId, String senderName,
                                  Long conversationId, Long messageId, String rawBody) {
        if (recipientUserId == null || senderUserId == null || recipientUserId.equals(senderUserId)) {
            return;
        }
        User recipient = userRepository.findByIdWithRoles(recipientUserId).orElse(null);
        if (recipient == null) {
            return;
        }
        boolean spanish = recipient.getLocale() == null || recipient.getLocale().toLowerCase().startsWith("es");
        String safeBody = MessagePreview.forNotification(rawBody, recipient.isMessagePreviewEnabled(), spanish);
        String name = clip(senderName, 80);
        String title = clip(spanish ? "Nuevo mensaje de " + name : "New message from " + name, 180);
        AppNotification notification = new AppNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(recipientUserId);
        notification.setType("NEW_MESSAGE");
        notification.setTitleEs(spanish ? title : clip("Nuevo mensaje de " + name, 180));
        notification.setTitleEn(spanish ? clip("New message from " + name, 180) : title);
        notification.setBodyEs(spanish ? safeBody : MessagePreview.forNotification(rawBody, recipient.isMessagePreviewEnabled(), true));
        notification.setBodyEn(spanish ? MessagePreview.forNotification(rawBody, recipient.isMessagePreviewEnabled(), false) : safeBody);
        notification.setEntityType("CONVERSATION");
        notification.setEntityId(conversationId);
        notificationRepository.save(notification);
        if (!recipient.isMessagePushEnabled()) {
            return;
        }
        fcmPushService.schedule(new FcmPushService.ChatPush(
                recipientUserId,
                senderUserId,
                tenantId,
                conversationId,
                messageId,
                title,
                safeBody,
                recipient.isMessageSoundEnabled(),
                badgeFor(recipient, tenantId)
        ));
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> mine(Pageable pageable) {
        String locale = TenantContext.get().locale();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(TenantContext.userId(), pageable)
                .map(n -> NotificationDto.from(n, locale));
    }

    @Transactional
    public void markConversationRead(Long userId, Long conversationId) {
        notificationRepository.markReadByEntity(userId, "CONVERSATION", conversationId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(TenantContext.userId());
    }

    @Transactional
    public void markRead(Long id) {
        notificationRepository.findByIdAndUserId(id, TenantContext.userId()).ifPresent(n -> n.setReadAt(Instant.now()));
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(TenantContext.userId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public NotificationPreferences preferences() {
        User user = userRepository.findById(TenantContext.userId())
                .orElseThrow();
        return NotificationPreferences.from(user);
    }

    @Transactional
    public NotificationPreferences updatePreferences(Boolean messagePushEnabled, Boolean messagePreviewEnabled, Boolean messageSoundEnabled) {
        User user = userRepository.findById(TenantContext.userId())
                .orElseThrow();
        if (messagePushEnabled != null) {
            user.setMessagePushEnabled(messagePushEnabled);
        }
        if (messagePreviewEnabled != null) {
            user.setMessagePreviewEnabled(messagePreviewEnabled);
        }
        if (messageSoundEnabled != null) {
            user.setMessageSoundEnabled(messageSoundEnabled);
        }
        return NotificationPreferences.from(user);
    }

    @Transactional
    public void registerPushToken(String token, String platform) {
        deviceService.registerLegacy(token, platform);
    }

    @Transactional
    public void unregisterPushToken(String token) {
        deviceService.deactivateLegacy(token);
    }

    private int badgeFor(User recipient, Long tenantId) {
        boolean staff = recipient.getRoles().stream().anyMatch(role -> STAFF_ROLES.contains(role.getCode()));
        long count = staff
                ? messageRepository.countUnreadForTenantUser(tenantId, recipient.getId())
                : messageRepository.countUnreadForParticipant(recipient.getId());
        if (count < 0) {
            return 0;
        }
        return (int) Math.min(count, 99);
    }

    private String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public record NotificationPreferences(boolean messagePushEnabled, boolean messagePreviewEnabled, boolean messageSoundEnabled) {
        static NotificationPreferences from(User user) {
            return new NotificationPreferences(user.isMessagePushEnabled(), user.isMessagePreviewEnabled(), user.isMessageSoundEnabled());
        }
    }

    public record NotificationDto(Long id, String type, String title, String body, String entityType, Long entityId,
                                  Instant createdAt, Instant readAt) {
        static NotificationDto from(AppNotification n, String locale) {
            boolean en = "en".equalsIgnoreCase(locale);
            return new NotificationDto(n.getId(), n.getType(),
                    en ? n.getTitleEn() : n.getTitleEs(),
                    en ? n.getBodyEn() : n.getBodyEs(),
                    n.getEntityType(), n.getEntityId(), n.getCreatedAt(), n.getReadAt());
        }
    }
}
