package com.animalin.common.domain;

import com.animalin.security.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@MappedSuperclass
@SQLRestriction("deleted = false")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @PrePersist
    void assignTenant() {
        if (tenantId == null) {
            Long current = TenantContext.tenantIdOrNull();
            if (current != null) {
                tenantId = current;
            }
        }
    }

    public Long getTenantId() {
        return tenantId;
    }
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
