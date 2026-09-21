package com.animalin.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Object mine(Pageable pageable) {
        return notificationService.mine(pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        notificationService.markRead(id);
    }

    @PostMapping("/push-token")
    public void pushToken(@RequestBody Map<String, String> body) {
        notificationService.registerPushToken(body == null ? null : body.get("token"),
                body == null ? null : body.get("platform"));
    }

    @DeleteMapping("/push-token")
    public void deletePushToken(@RequestBody(required = false) Map<String, String> body) {
        notificationService.unregisterPushToken(body == null ? null : body.get("token"));
    }
}
