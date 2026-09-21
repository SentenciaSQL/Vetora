package com.animalin.messaging;

import com.animalin.common.api.PageResponse;
import com.animalin.common.exception.ApiException;
import com.animalin.dto.AppDtos;
import com.animalin.notification.NotificationService;
import com.animalin.owner.Owner;
import com.animalin.pet.Pet;
import com.animalin.plan.PlanLimitService;
import com.animalin.security.AccessGuard;
import com.animalin.security.TenantContext;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;
    private final PlanLimitService planLimitService;
    private final Clock clock;

    public MessagingService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                            ConversationReadStateRepository readStateRepository, UserRepository userRepository,
                            TenantRepository tenantRepository, AccessGuard accessGuard,
                            NotificationService notificationService, PlanLimitService planLimitService, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
        this.planLimitService = planLimitService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list() {
        denySuperAdmin();
        List<Conversation> conversations = accessGuard.isOwnerContext()
                ? conversationRepository.findByParticipant(TenantContext.userId())
                : listStaffConversations();
        if (conversations.isEmpty()) {
            return List.of();
        }
        Set<Long> tenantIds = conversations.stream().map(Conversation::getTenantId).collect(Collectors.toSet());
        Map<Long, Tenant> tenants = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        Long userId = TenantContext.userId();
        List<Long> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<Long, ConversationReadState> reads = conversationIds.isEmpty()
                ? Map.of()
                : readStateRepository.findByUserIdAndConversationIdIn(userId, conversationIds).stream()
                .collect(Collectors.toMap(state -> state.getConversation().getId(), Function.identity(), (a, b) -> a));
        return conversations.stream().map(conversation -> toSummary(conversation, tenants.get(conversation.getTenantId()),
                reads.get(conversation.getId()), userId)).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        denySuperAdmin();
        Long userId = TenantContext.userId();
        long count = accessGuard.isOwnerContext()
                ? messageRepository.countUnreadForParticipant(userId)
                : messageRepository.countUnreadForTenantUser(accessGuard.requireStaffTenant(), userId);
        return new UnreadCountResponse(count);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppDtos.MessageResponse> messages(Long id, Pageable pageable, Long afterId) {
        Conversation conversation = requireConversation(id);
        planLimitService.assertMessagingEnabled(conversation.getTenantId());
        if (afterId != null && afterId > 0) {
            List<AppDtos.MessageResponse> items = messageRepository
                    .findByConversationIdAndIdGreaterThanOrderByCreatedAtAsc(conversation.getId(), afterId)
                    .stream()
                    .map(this::toMessage)
                    .toList();
            return new PageResponse<>(items, 0, items.size(), items.size(), 1);
        }
        Pageable page = pageable == null || pageable.isUnpaged()
                ? PageRequest.of(0, 50)
                : PageRequest.of(pageable.getPageNumber(), Math.min(100, Math.max(1, pageable.getPageSize())));
        Page<Message> newestFirst = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId(), page);
        List<AppDtos.MessageResponse> chronological = new ArrayList<>(newestFirst.getContent().stream().map(this::toMessage).toList());
        Collections.reverse(chronological);
        return new PageResponse<>(chronological, newestFirst.getNumber(), newestFirst.getSize(),
                newestFirst.getTotalElements(), newestFirst.getTotalPages());
    }

    @Transactional
    public ConversationSummary markRead(Long conversationId) {
        Conversation conversation = requireConversation(conversationId);
        User user = userRepository.getReferenceById(TenantContext.userId());
        Instant now = clock.instant();
        Long lastId = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(Message::getId)
                .orElse(null);
        ConversationReadState state = readStateRepository
                .findByConversationIdAndUserId(conversation.getId(), user.getId())
                .orElseGet(() -> {
                    ConversationReadState created = new ConversationReadState();
                    created.setTenantId(conversation.getTenantId());
                    created.setConversation(conversation);
                    created.setUser(user);
                    return created;
                });
        state.setLastReadAt(now);
        state.setLastReadMessageId(lastId);
        readStateRepository.save(state);
        notificationService.markConversationRead(user.getId(), conversation.getId());
        Tenant tenant = tenantRepository.findById(conversation.getTenantId()).orElse(null);
        return toSummary(conversation, tenant, state, user.getId());
    }

    @Transactional
    public ConversationSummary create(CreateRequest request) {
        if (!accessGuard.isOwnerContext()) {
            accessGuard.requirePermission("MESSAGE_WRITE");
        }
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
        conversation.setSubject(trimTo(request.subject(), 240));
        conversation.getParticipants().add(userRepository.getReferenceById(TenantContext.userId()));
        if (owner.getUser() != null) {
            conversation.getParticipants().add(owner.getUser());
        }
        conversationRepository.save(conversation);
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        return toSummary(conversation, tenant, null, TenantContext.userId());
    }

    @Transactional
    public AppDtos.MessageResponse send(Long conversationId, SendRequest request) {
        Conversation conversation = requireConversation(conversationId);
        if (!accessGuard.isOwnerContext()) {
            accessGuard.requirePermission("MESSAGE_WRITE");
        }
        String body = request == null || request.body() == null ? "" : request.body().trim();
        if (body.isEmpty()) {
            throw ApiException.badRequest("El mensaje no puede estar vacío");
        }
        if (body.length() > 4000) {
            throw ApiException.badRequest("El mensaje supera el máximo de 4000 caracteres");
        }
        planLimitService.assertCanSendMessage(conversation.getTenantId());
        User sender = userRepository.getReferenceById(TenantContext.userId());
        ensureParticipant(conversation, sender);
        Message message = new Message();
        message.setTenantId(conversation.getTenantId());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(body);
        message.setPetId(conversation.getPet() == null ? null : conversation.getPet().getId());
        messageRepository.save(message);
        conversation.setUpdatedAt(clock.instant());
        ConversationReadState own = readStateRepository
                .findByConversationIdAndUserId(conversation.getId(), sender.getId())
                .orElseGet(() -> {
                    ConversationReadState created = new ConversationReadState();
                    created.setTenantId(conversation.getTenantId());
                    created.setConversation(conversation);
                    created.setUser(sender);
                    return created;
                });
        own.setLastReadAt(clock.instant());
        own.setLastReadMessageId(message.getId());
        readStateRepository.save(own);
        List<Long> recipientIds = conversation.getParticipants().stream()
                .map(User::getId)
                .filter(userId -> userId != null && !userId.equals(sender.getId()))
                .distinct()
                .toList();
        for (Long userId : recipientIds) {
            notificationService.notifyChatMessage(
                    conversation.getTenantId(),
                    userId,
                    sender.getId(),
                    sender.fullName(),
                    conversation.getId(),
                    message.getId(),
                    body);
        }
        return toMessage(message);
    }

    private List<Conversation> listStaffConversations() {
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("MESSAGE_READ");
        planLimitService.assertMessagingEnabled(tenantId);
        return conversationRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
    }

    private Conversation requireConversation(Long id) {
        denySuperAdmin();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conversación no encontrada"));
        if (accessGuard.isOwnerContext()) {
            if (!participates(conversation, TenantContext.userId())) {
                throw ApiException.notFound("Conversación no encontrada");
            }
            return conversation;
        }
        Long tenantId = accessGuard.requireStaffTenant();
        accessGuard.requirePermission("MESSAGE_READ");
        if (!Objects.equals(tenantId, conversation.getTenantId())) {
            throw ApiException.notFound("Conversación no encontrada");
        }
        return conversation;
    }

    private boolean participates(Conversation conversation, Long userId) {
        if (conversation.getParticipants().stream().anyMatch(user -> user.getId().equals(userId))) {
            return true;
        }
        return conversation.getOwner() != null
                && conversation.getOwner().getUser() != null
                && conversation.getOwner().getUser().getId().equals(userId);
    }

    private void ensureParticipant(Conversation conversation, User user) {
        if (conversation.getParticipants().stream().noneMatch(existing -> existing.getId().equals(user.getId()))) {
            conversation.getParticipants().add(user);
        }
    }

    private void denySuperAdmin() {
        if (TenantContext.isSuperAdmin()) {
            throw ApiException.forbidden("El administrador de plataforma no accede al contenido privado de las conversaciones");
        }
    }

    private ConversationSummary toSummary(Conversation conversation, Tenant tenant, ConversationReadState read, Long userId) {
        Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId()).orElse(null);
        Instant lastReadAt = read == null ? null : read.getLastReadAt();
        long unread = unreadInConversation(conversation.getId(), userId, lastReadAt);
        String tenantName = tenant == null ? "" : (tenant.getCommercialName() == null || tenant.getCommercialName().isBlank()
                ? tenant.getName() : tenant.getCommercialName());
        String ownerName = conversation.getOwner() == null ? "" : conversation.getOwner().fullName();
        boolean ownerView = accessGuard.isOwnerContext();
        String preview = last == null || last.getBody() == null ? "" : last.getBody();
        return new ConversationSummary(
                conversation.getId(),
                conversation.getSubject() == null ? "" : conversation.getSubject(),
                conversation.getTenantId(),
                tenantName,
                conversation.getPet() == null ? "" : conversation.getPet().getName(),
                ownerName,
                ownerView ? tenantName : ownerName,
                preview,
                conversation.getUpdatedAt(),
                unread
        );
    }

    private long unreadInConversation(Long conversationId, Long userId, Instant lastReadAt) {
        if (lastReadAt == null) {
            return messageRepository.countByConversationIdAndSenderIdNot(conversationId, userId);
        }
        return messageRepository.countByConversationIdAndSenderIdNotAndCreatedAtAfter(conversationId, userId, lastReadAt);
    }

    private AppDtos.MessageResponse toMessage(Message message) {
        return new AppDtos.MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().fullName(),
                message.getBody(),
                message.getCreatedAt(),
                message.getReadAt()
        );
    }

    private String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public record CreateRequest(Long ownerId, Long petId, String subject) {
    }

    public record SendRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record ConversationSummary(
            Long id,
            String subject,
            Long tenantId,
            String tenantName,
            String petName,
            String ownerName,
            String title,
            String lastMessage,
            Instant updatedAt,
            long unread
    ) {
    }

    public record UnreadCountResponse(long count) {
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
    public List<MessagingService.ConversationSummary> list() {
        return messagingService.list();
    }

    @GetMapping("/unread-count")
    public MessagingService.UnreadCountResponse unreadCount() {
        return messagingService.unreadCount();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessagingService.ConversationSummary create(@RequestBody MessagingService.CreateRequest request) {
        return messagingService.create(request);
    }

    @GetMapping("/{id:\\d+}")
    public PageResponse<AppDtos.MessageResponse> messages(@PathVariable Long id,
                                                          Pageable pageable,
                                                          @RequestParam(required = false) Long afterId) {
        return messagingService.messages(id, pageable, afterId);
    }

    @PostMapping("/{id:\\d+}/read")
    public MessagingService.ConversationSummary markRead(@PathVariable Long id) {
        return messagingService.markRead(id);
    }

    @PostMapping("/{id:\\d+}")
    public AppDtos.MessageResponse send(@PathVariable Long id, @Valid @RequestBody MessagingService.SendRequest request) {
        return messagingService.send(id, request);
    }
}
