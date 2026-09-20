package com.animalin.messaging;

import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.notification.NotificationService;
import com.animalin.owner.Owner;
import com.animalin.pet.Pet;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;
    private final PlanLimitService planLimitService;

    public MessagingService(ConversationRepository conversationRepository, MessageRepository messageRepository, UserRepository userRepository, AccessGuard accessGuard, NotificationService notificationService, PlanLimitService planLimitService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
        this.planLimitService = planLimitService;
    }


    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Conversation> conversations = accessGuard.isOwnerContext()
                ? conversationRepository.findByParticipant(TenantContext.userId())
                : conversationRepository.findByTenantIdOrderByUpdatedAtDesc(accessGuard.requireStaffTenant());
        return conversations.stream().map(c -> {
            List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getId());
            Message last = messages.isEmpty() ? null : messages.getLast();
            long unread = messageRepository.countByConversationIdAndReadAtIsNullAndSenderIdNot(c.getId(), TenantContext.userId());
            return Map.<String, Object>of(
                    "id", c.getId(),
                    "subject", c.getSubject() == null ? "" : c.getSubject(),
                    "tenantId", c.getTenantId(),
                    "petName", c.getPet() == null ? "" : c.getPet().getName(),
                    "ownerName", c.getOwner() == null ? "" : c.getOwner().fullName(),
                    "lastMessage", last == null ? "" : last.getBody(),
                    "updatedAt", c.getUpdatedAt(),
                    "unread", unread
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AppDtos.MessageResponse> messages(Long id) {
        Conversation conversation = requireConversation(id);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(m -> new AppDtos.MessageResponse(
                        m.getId(),
                        m.getSender().getId(),
                        m.getSender().fullName(),
                        m.getBody(),
                        m.getCreatedAt(),
                        m.getReadAt()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        Long tenantId;
        Owner owner;
        Pet pet = null;
        if (request.petId() != null) {
            pet = accessGuard.requirePet(request.petId());
            owner = pet.getOwner();
            tenantId = pet.getTenantId();
        } else {
            owner = accessGuard.requireOwner(request.ownerId());
            tenantId = owner.getTenantId();
        }
        planLimitService.assertMessagingEnabled(tenantId);
        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setOwner(owner);
        conversation.setPet(pet);
        conversation.setSubject(request.subject());
        conversation.getParticipants().add(userRepository.getReferenceById(TenantContext.userId()));
        if (owner.getUser() != null) {
            conversation.getParticipants().add(owner.getUser());
        }
        conversationRepository.save(conversation);
        return Map.of(
                "id", conversation.getId(),
                "subject", conversation.getSubject() == null ? "" : conversation.getSubject(),
                "tenantId", conversation.getTenantId(),
                "ownerName", owner.fullName(),
                "petName", pet == null ? "" : pet.getName(),
                "lastMessage", "",
                "unread", 0L
        );
    }

    @Transactional
    public AppDtos.MessageResponse send(Long conversationId, SendRequest request) {
        Conversation conversation = requireConversation(conversationId);
        User sender = userRepository.getReferenceById(TenantContext.userId());
        Message message = new Message();
        message.setTenantId(conversation.getTenantId());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(request.body());
        message.setPetId(conversation.getPet() == null ? null : conversation.getPet().getId());
        messageRepository.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversation.getParticipants().stream()
                .filter(u -> !u.getId().equals(sender.getId()))
                .forEach(u -> notificationService.notifyUser(conversation.getTenantId(), u.getId(),
                        "NEW_MESSAGE", "Nuevo mensaje", "New message",
                        request.body(), request.body(), "CONVERSATION", conversation.getId()));
        return new AppDtos.MessageResponse(
                message.getId(),
                sender.getId(),
                sender.fullName(),
                message.getBody(),
                message.getCreatedAt(),
                message.getReadAt()
        );
    }

    private Conversation requireConversation(Long id) {
        if (accessGuard.isOwnerContext()) {
            Conversation conversation = conversationRepository.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Conversación no encontrada"));
            boolean participant = conversation.getParticipants().stream().anyMatch(u -> u.getId().equals(TenantContext.userId()));
            if (!participant) {
                throw ApiException.notFound("Conversación no encontrada");
            }
            return conversation;
        }
        return conversationRepository.findByIdAndTenantId(id, accessGuard.requireStaffTenant())
                .orElseThrow(() -> ApiException.notFound("Conversación no encontrada"));
    }

    public record CreateRequest(Long ownerId, Long petId, String subject) {
    }

    public record SendRequest(String body) {
    }
}

@RestController
@RequestMapping("/api/v1/messages")
class MessagingController {
    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }


    @GetMapping
    public List<Map<String, Object>> list() {
        return messagingService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody MessagingService.CreateRequest request) {
        return messagingService.create(request);
    }

    @GetMapping("/{id}")
    public List<AppDtos.MessageResponse> messages(@PathVariable Long id) {
        return messagingService.messages(id);
    }

    @PostMapping("/{id}")
    public AppDtos.MessageResponse send(@PathVariable Long id, @RequestBody MessagingService.SendRequest request) {
        return messagingService.send(id, request);
    }
}
