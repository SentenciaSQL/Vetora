package com.animalin.signup;

import com.animalin.plan.Plan;
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
@Table(name = "clinic_signups")
public class ClinicSignup {

    public static final String PENDING_EMAIL_VERIFICATION = "PENDING_EMAIL_VERIFICATION";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String COMPLETED = "COMPLETED";
    public static final String ABANDONED = "ABANDONED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 40)
    private String status = PENDING_EMAIL_VERIFICATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle;

    @Column(name = "requested_slug", length = 80)
    private String requestedSlug;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "last_verification_sent_at")
    private Instant lastVerificationSentAt;

    @Column(name = "checkout_created_at")
    private Instant checkoutCreatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    public Tenant getTenant() {
        return tenant;
    }
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public Plan getPlan() {
        return plan;
    }
    public void setPlan(Plan plan) {
        this.plan = plan;
    }
    public String getBillingCycle() {
        return billingCycle;
    }
    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }
    public String getRequestedSlug() {
        return requestedSlug;
    }
    public void setRequestedSlug(String requestedSlug) {
        this.requestedSlug = requestedSlug;
    }
    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }
    public void setTermsAcceptedAt(Instant termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }
    public Instant getLastVerificationSentAt() {
        return lastVerificationSentAt;
    }
    public void setLastVerificationSentAt(Instant lastVerificationSentAt) {
        this.lastVerificationSentAt = lastVerificationSentAt;
    }
    public Instant getCheckoutCreatedAt() {
        return checkoutCreatedAt;
    }
    public void setCheckoutCreatedAt(Instant checkoutCreatedAt) {
        this.checkoutCreatedAt = checkoutCreatedAt;
    }
    public Instant getCompletedAt() {
        return completedAt;
    }
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
