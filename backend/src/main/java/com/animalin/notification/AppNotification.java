package com.animalin.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "notifications")
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(name = "title_es", nullable = false)
    private String titleEs;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "body_es")
    private String bodyEs;

    @Column(name = "body_en")
    private String bodyEn;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getTenantId() {
        return tenantId;
    }
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getTitleEs() {
        return titleEs;
    }
    public void setTitleEs(String titleEs) {
        this.titleEs = titleEs;
    }
    public String getTitleEn() {
        return titleEn;
    }
    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }
    public String getBodyEs() {
        return bodyEs;
    }
    public void setBodyEs(String bodyEs) {
        this.bodyEs = bodyEs;
    }
    public String getBodyEn() {
        return bodyEn;
    }
    public void setBodyEn(String bodyEn) {
        this.bodyEn = bodyEn;
    }
    public String getEntityType() {
        return entityType;
    }
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    public Long getEntityId() {
        return entityId;
    }
    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }
    public Instant getReadAt() {
        return readAt;
    }
    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
