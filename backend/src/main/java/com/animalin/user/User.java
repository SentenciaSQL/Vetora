package com.animalin.user;

import com.animalin.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@SQLRestriction("deleted = false")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    private String phone;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 8)
    private String locale = "es";

    @Column(nullable = false, length = 16)
    private String theme = "system";

    @Column(name = "message_push_enabled", nullable = false)
    private boolean messagePushEnabled = true;

    @Column(name = "message_preview_enabled", nullable = false)
    private boolean messagePreviewEnabled = true;

    @Column(name = "message_sound_enabled", nullable = false)
    private boolean messageSoundEnabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "trial_used", nullable = false)
    private boolean trialUsed = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "deletion_status", nullable = false, length = 32)
    private String deletionStatus = "ACTIVE";

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public String getDocumentId() {
        return documentId;
    }
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    public String getLocale() {
        return locale;
    }
    public void setLocale(String locale) {
        this.locale = locale;
    }
    public String getTheme() {
        return theme;
    }
    public void setTheme(String theme) {
        this.theme = theme;
    }
    public boolean isMessagePushEnabled() {
        return messagePushEnabled;
    }
    public void setMessagePushEnabled(boolean messagePushEnabled) {
        this.messagePushEnabled = messagePushEnabled;
    }
    public boolean isMessagePreviewEnabled() {
        return messagePreviewEnabled;
    }
    public void setMessagePreviewEnabled(boolean messagePreviewEnabled) {
        this.messagePreviewEnabled = messagePreviewEnabled;
    }
    public boolean isMessageSoundEnabled() {
        return messageSoundEnabled;
    }
    public void setMessageSoundEnabled(boolean messageSoundEnabled) {
        this.messageSoundEnabled = messageSoundEnabled;
    }
    public boolean isEmailVerified() {
        return emailVerified;
    }
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    public boolean isTrialUsed() {
        return trialUsed;
    }
    public void setTrialUsed(boolean trialUsed) {
        this.trialUsed = trialUsed;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public String getDeletionStatus() {
        return deletionStatus;
    }
    public void setDeletionStatus(String deletionStatus) {
        this.deletionStatus = deletionStatus;
    }
    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }
    public void setAnonymizedAt(Instant anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }
    public boolean isAccountDeleted() {
        return "DELETED".equals(deletionStatus);
    }
    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    public Set<Role> getRoles() {
        return roles;
    }
    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }
}
