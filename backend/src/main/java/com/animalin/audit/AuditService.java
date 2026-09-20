package com.animalin.audit;

import com.animalin.security.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    @Query("select a from AuditLog a where (:tenantId is null or a.tenantId = :tenantId) order by a.createdAt desc")
    Page<AuditLog> search(Long tenantId, Pageable pageable);
}

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
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
    public Page<AuditLog> list(Long tenantId, Pageable pageable) {
        if (tenantId != null) {
            return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return repository.search(null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> listEntries(Long tenantId, Pageable pageable) {
        return list(tenantId, pageable).map(a -> new AuditEntry(
                a.getId(), a.getTenantId(), a.getUserId(), a.getUsername(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getOldValue(), a.getNewValue(), a.getCreatedAt()
        ));
    }

    public record AuditEntry(Long id, Long tenantId, Long userId, String username, String action, String entityType,
                             Long entityId, String details, String oldValue, String newValue, Instant createdAt) {
    }
}
