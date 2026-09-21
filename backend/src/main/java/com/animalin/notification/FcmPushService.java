package com.animalin.notification;

import com.animalin.messaging.ConversationRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    private static final int MULTICAST_LIMIT = 500;

    private final FirebaseAdminConfig firebaseAdminConfig;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final DeviceService deviceService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "fcm-push");
        thread.setDaemon(true);
        return thread;
    });

    public FcmPushService(FirebaseAdminConfig firebaseAdminConfig, PushTokenRepository pushTokenRepository,
                          UserRepository userRepository, ConversationRepository conversationRepository,
                          DeviceService deviceService, TransactionTemplate transactionTemplate) {
        this.firebaseAdminConfig = firebaseAdminConfig;
        this.pushTokenRepository = pushTokenRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.deviceService = deviceService;
        this.transactionTemplate = transactionTemplate;
    }

    public void schedule(ChatPush push) {
        Runnable task = () -> executor.execute(() -> deliver(push));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void deliver(ChatPush push) {
        try {
            if (!firebaseAdminConfig.isReady()) {
                log.warn("FCM not ready; chat push skipped userId={} conversationId={} messageId={}",
                        push.recipientUserId(), push.conversationId(), push.messageId());
                return;
            }
            List<DeviceRef> devices = loadDevices(push);
            if (devices == null) {
                return;
            }
            if (devices.isEmpty()) {
                log.info("Chat push skipped userId={} conversationId={} messageId={} reason=no-devices",
                        push.recipientUserId(), push.conversationId(), push.messageId());
                return;
            }
            for (int start = 0; start < devices.size(); start += MULTICAST_LIMIT) {
                List<DeviceRef> chunk = devices.subList(start, Math.min(start + MULTICAST_LIMIT, devices.size()));
                sendChunk(push, chunk);
            }
        } catch (RuntimeException ex) {
            log.warn("Chat push failed userId={} conversationId={} messageId={} cause={}",
                    push.recipientUserId(), push.conversationId(), push.messageId(), ex.getClass().getSimpleName());
        }
    }

    private List<DeviceRef> loadDevices(ChatPush push) {
        return transactionTemplate.execute(status -> {
            User recipient = userRepository.findById(push.recipientUserId()).orElse(null);
            if (recipient == null || !recipient.isEnabled() || recipient.isAccountDeleted()) {
                log.info("Chat push skipped userId={} conversationId={} reason=recipient-unavailable",
                        push.recipientUserId(), push.conversationId());
                return null;
            }
            if (push.senderUserId().equals(recipient.getId())) {
                log.info("Chat push skipped userId={} conversationId={} reason=sender",
                        push.recipientUserId(), push.conversationId());
                return null;
            }
            if (!recipient.isMessagePushEnabled()) {
                log.info("Chat push skipped userId={} conversationId={} reason=push-disabled",
                        push.recipientUserId(), push.conversationId());
                return null;
            }
            long membership = conversationRepository.countParticipant(push.conversationId(), push.tenantId(), recipient.getId());
            if (membership <= 0) {
                log.info("Chat push skipped userId={} conversationId={} reason=not-participant",
                        push.recipientUserId(), push.conversationId());
                return null;
            }
            List<DeviceRef> refs = new ArrayList<>();
            for (PushToken device : pushTokenRepository.findByUserIdAndActiveTrue(recipient.getId())) {
                if (device.getToken() == null || device.getToken().isBlank() || !device.isActive()) {
                    continue;
                }
                refs.add(new DeviceRef(device.getId(), device.getToken()));
            }
            return List.copyOf(refs);
        });
    }

    private void sendChunk(ChatPush push, List<DeviceRef> devices) {
        List<String> tokens = devices.stream().map(DeviceRef::token).toList();
        String collapse = "chat-" + push.messageId();
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(push.title()).setBody(push.body()).build())
                .putAllData(Map.of(
                        "type", "CHAT_MESSAGE",
                        "conversationId", Long.toString(push.conversationId()),
                        "messageId", Long.toString(push.messageId()),
                        "route", "/messages"
                ))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setCollapseKey(collapse)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(push.sound() ? "lunaveta_messages" : "lunaveta_messages_silent")
                                .setTag(collapse)
                                .setIcon("ic_stat_lunaveta")
                                .setColor("#0F766E")
                                .setNotificationCount(push.badge())
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .putHeader("apns-collapse-id", collapse)
                        .setAps(aps(push))
                        .build())
                .build();
        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            List<Long> permanent = new ArrayList<>();
            List<SendResponse> results = response.getResponses();
            for (int i = 0; i < results.size(); i++) {
                SendResponse result = results.get(i);
                if (result.isSuccessful()) {
                    continue;
                }
                FirebaseMessagingException error = result.getException();
                MessagingErrorCode code = error == null ? null : error.getMessagingErrorCode();
                if (permanent(error)) {
                    permanent.add(devices.get(i).id());
                    log.warn("FCM permanent failure userId={} deviceId={} code={}",
                            push.recipientUserId(), devices.get(i).id(), code);
                } else {
                    log.warn("FCM temporary failure userId={} deviceId={} code={}",
                            push.recipientUserId(), devices.get(i).id(), code);
                }
            }
            if (!permanent.isEmpty()) {
                deviceService.deactivateIds(permanent);
            }
            log.info("Chat push sent userId={} conversationId={} messageId={} devices={} success={} failed={}",
                    push.recipientUserId(), push.conversationId(), push.messageId(), devices.size(),
                    response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException ex) {
            log.warn("FCM temporary failure userId={} conversationId={} messageId={} code={}",
                    push.recipientUserId(), push.conversationId(), push.messageId(), ex.getMessagingErrorCode());
        }
    }

    private Aps aps(ChatPush push) {
        Aps.Builder builder = Aps.builder()
                .setBadge(push.badge())
                .setThreadId("conversation-" + push.conversationId());
        if (push.sound()) {
            builder.setSound("default");
        }
        return builder.build();
    }

    private boolean permanent(FirebaseMessagingException error) {
        if (error == null || error.getMessagingErrorCode() == null) {
            return false;
        }
        MessagingErrorCode code = error.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return true;
        }
        if (code != MessagingErrorCode.INVALID_ARGUMENT) {
            return false;
        }
        String detail = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return detail.contains("registration token") || detail.contains("not a valid");
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public record ChatPush(
            Long recipientUserId,
            Long senderUserId,
            Long tenantId,
            Long conversationId,
            Long messageId,
            String title,
            String body,
            boolean sound,
            int badge
    ) {
    }

    private record DeviceRef(Long id, String token) {
    }
}
