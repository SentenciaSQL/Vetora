package com.animalin.audit;

import com.animalin.security.TenantContext;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "audit_logs")
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id")
    private Long tenantId;
    @Column(name = "user_id")
    private Long userId;
    private String username;
    @Column(nullable = false)
    private String action;
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;
    private String details;
    @Column(name = "old_value")
    private String oldValue;
    @Column(name = "new_value")
    private String newValue;
    @Column(name = "ip_address")
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    @Query("select distinct a.action from AuditLog a where a.tenantId = :tenantId order by a.action")
    List<String> distinctActions(@Param("tenantId") Long tenantId);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> distinctActionsAll();

    @Query("select distinct a.entityType from AuditLog a where a.tenantId = :tenantId order by a.entityType")
    List<String> distinctEntities(@Param("tenantId") Long tenantId);

    @Query("select distinct a.entityType from AuditLog a order by a.entityType")
    List<String> distinctEntitiesAll();

    @Query("""
            select distinct a.userId, a.username from AuditLog a
            where a.userId is not null and a.tenantId = :tenantId
            """)
    List<Object[]> distinctUsers(@Param("tenantId") Long tenantId);

    @Query("select distinct a.userId, a.username from AuditLog a where a.userId is not null")
    List<Object[]> distinctUsersAll();
}

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }


    @Transactional
    public void record(Long tenantId, Long userId, String username, String action, String entityType, Long entityId,
                       String details, String oldValue, String newValue) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(clientIp());
        repository.save(log);
    }

    public void record(String action, String entityType, Long entityId, String details) {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        record(
                TenantContext.tenantIdOrNull(),
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.email(),
                action, entityType, entityId, details, null, null
        );
    }

    public void recordChange(String action, String entityType, Long entityId, String field, String oldValue, String newValue) {
        TenantContext.AuthPrincipal principal = TenantContext.getOrNull();
        record(
                TenantContext.tenantIdOrNull(),
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.email(),
                action, entityType, entityId, field, oldValue, newValue
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> listEntries(Long tenantId, Pageable pageable) {
        return search(tenantId, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(Long tenantId, String search, String action, String entityType, Long userId,
                                   Instant from, Instant to, Pageable pageable) {
        String term = blankToNull(search);
        String pattern = like(term);
        List<Long> matchedUsers = pattern == null ? List.of() : userRepository.findIdsMatching(pattern);
        Page<AuditLog> page = repository.findAll(
                specification(tenantId, pattern, matchedUsers, blankToNull(action), blankToNull(entityType), userId, from, to),
                pageable
        );
        Map<Long, User> users = usersById(page.getContent());
        return page.map(log -> toEntry(log, users.get(log.getUserId())));
    }

    private Specification<AuditLog> specification(Long tenantId, String pattern, List<Long> matchedUsers, String action,
                                                  String entityType, Long userId, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            if (pattern != null) {
                List<Predicate> matches = new ArrayList<>();
                matches.add(cb.like(cb.lower(cb.coalesce(root.get("username"), "")), pattern, '\\'));
                matches.add(cb.like(cb.lower(cb.coalesce(root.get("action"), "")), pattern, '\\'));
                matches.add(cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), pattern, '\\'));
                matches.add(cb.like(cb.lower(cb.coalesce(root.get("details"), "")), pattern, '\\'));
                matches.add(cb.like(root.get("entityId").as(String.class), pattern, '\\'));
                if (!matchedUsers.isEmpty()) {
                    matches.add(root.get("userId").in(matchedUsers));
                }
                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public AuditFilterOptions filters(Long tenantId) {
        List<Object[]> rawUsers = tenantId == null ? repository.distinctUsersAll() : repository.distinctUsers(tenantId);
        List<AuditUserOption> users = rawUsers.stream()
                .map(row -> {
                    Long id = (Long) row[0];
                    String email = row[1] == null ? null : row[1].toString();
                    return new AuditUserOption(id, email, email);
                })
                .toList();
        if (!users.isEmpty()) {
            Map<Long, User> known = userRepository.findAllById(users.stream().map(AuditUserOption::id).toList()).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            users = users.stream()
                    .map(option -> {
                        User user = known.get(option.id());
                        if (user == null) {
                            return option;
                        }
                        return new AuditUserOption(option.id(), user.fullName(), user.getEmail());
                    })
                    .toList();
        }
        return new AuditFilterOptions(
                tenantId == null ? repository.distinctActionsAll() : repository.distinctActions(tenantId),
                tenantId == null ? repository.distinctEntitiesAll() : repository.distinctEntities(tenantId),
                users
        );
    }

    private Map<Long, User> usersById(List<AuditLog> logs) {
        List<Long> ids = logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AuditEntry toEntry(AuditLog log, User user) {
        boolean hide = sensitive(log.getDetails());
        String name = user == null ? null : user.fullName();
        String email = user != null && user.getEmail() != null ? user.getEmail() : log.getUsername();
        return new AuditEntry(
                log.getId(),
                log.getTenantId(),
                log.getUserId(),
                email,
                name == null || name.isBlank() ? email : name,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetails(),
                hide ? null : log.getOldValue(),
                hide ? null : log.getNewValue(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }

    private static boolean sensitive(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("credential");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String like(String value) {
        String term = blankToNull(value);
        if (term == null) {
            return null;
        }
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record AuditEntry(Long id, Long tenantId, Long userId, String username, String userName, String action,
                             String entityType, Long entityId, String details, String oldValue, String newValue,
                             String ipAddress, Instant createdAt) {
    }

    public record AuditUserOption(Long id, String name, String email) {
    }

    public record AuditFilterOptions(List<String> actions, List<String> entities, List<AuditUserOption> users) {
    }
}
