package com.animalin.employee;

import com.animalin.tenant.Tenant;
import com.animalin.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "staff_invitations")
public class StaffInvitation {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(name = "specialty_code", length = 40)
    private String specialtyCode;

    @Column(name = "specialty_other", length = 160)
    private String specialtyOther;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }
    public Tenant getTenant() {
        return tenant;
    }
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }
    public User getInvitedBy() {
        return invitedBy;
    }
    public void setInvitedBy(User invitedBy) {
        this.invitedBy = invitedBy;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public String getRoleCode() {
        return roleCode;
    }
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
    public String getSpecialtyCode() {
        return specialtyCode;
    }
    public void setSpecialtyCode(String specialtyCode) {
        this.specialtyCode = specialtyCode;
    }
    public String getSpecialtyOther() {
        return specialtyOther;
    }
    public void setSpecialtyOther(String specialtyOther) {
        this.specialtyOther = specialtyOther;
    }
    public Long getBranchId() {
        return branchId;
    }
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
    public String getTokenHash() {
        return tokenHash;
    }
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
    public Instant getAcceptedAt() {
        return acceptedAt;
    }
    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
    public User getAcceptedBy() {
        return acceptedBy;
    }
    public void setAcceptedBy(User acceptedBy) {
        this.acceptedBy = acceptedBy;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
