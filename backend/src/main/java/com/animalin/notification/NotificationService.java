package com.animalin.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.animalin.config.AnimalinProperties;
import com.animalin.security.TenantContext;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);


    private final AppNotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final AnimalinProperties properties;
    private final Optional<JavaMailSender> mailSender;

    public NotificationService(AppNotificationRepository notificationRepository, PushTokenRepository pushTokenRepository, UserRepository userRepository, AnimalinProperties properties, Optional<JavaMailSender> mailSender) {
        this.notificationRepository = notificationRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.mailSender = mailSender;
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
        sendPushPrepared(userId, titleEs, bodyEs);
        sendEmailPrepared(userId, titleEs, bodyEs);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> mine(Pageable pageable) {
        String locale = TenantContext.get().locale();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(TenantContext.userId(), pageable)
                .map(n -> NotificationDto.from(n, locale));
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
    public void registerPushToken(String token, String platform) {
        User user = userRepository.findById(TenantContext.userId()).orElseThrow();
        PushToken push = new PushToken();
        push.setUser(user);
        push.setToken(token);
        push.setPlatform(platform);
        pushTokenRepository.save(push);
    }

    private void sendPushPrepared(Long userId, String title, String body) {
        if (!properties.fcm().enabled()) {
            log.debug("FCM disabled, skip push for user {} - {} {}", userId, title, body);
            return;
        }
        pushTokenRepository.findByUserId(userId).forEach(token ->
                log.info("Would send FCM to {} ({})", token.getToken(), title));
    }

    public void sendPlainEmail(String to, String subject, String body) {
        mailSender.ifPresentOrElse(sender -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
            } catch (Exception ex) {
                log.info("Email not delivered to {} (integration prepared): {}", to, ex.getMessage());
            }
        }, () -> log.info("Email prepared for {} [{}]", to, subject));
    }

    private void sendEmailPrepared(Long userId, String title, String body) {
        mailSender.ifPresent(sender -> userRepository.findById(userId).ifPresent(user -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject(title);
                message.setText(body);
                sender.send(message);
            } catch (Exception ex) {
                log.debug("Email not sent (integration prepared): {}", ex.getMessage());
            }
        }));
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
