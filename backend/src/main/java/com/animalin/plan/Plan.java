package com.animalin.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_es")
    private String descriptionEs;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_veterinarians", nullable = false)
    private int maxVeterinarians;

    @Column(name = "max_branches", nullable = false)
    private int maxBranches;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "max_messages_month", nullable = false)
    private int maxMessagesMonth;

    @Column(name = "reports_enabled", nullable = false)
    private boolean reportsEnabled;

    @Column(name = "messaging_enabled", nullable = false)
    private boolean messagingEnabled;

    @Column(name = "laboratory_enabled", nullable = false)
    private boolean laboratoryEnabled;

    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getNameEs() {
        return nameEs;
    }
    public void setNameEs(String nameEs) {
        this.nameEs = nameEs;
    }
    public String getNameEn() {
        return nameEn;
    }
    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }
    public String getDescriptionEs() {
        return descriptionEs;
    }
    public void setDescriptionEs(String descriptionEs) {
        this.descriptionEs = descriptionEs;
    }
    public String getDescriptionEn() {
        return descriptionEn;
    }
    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }
    public int getMaxUsers() {
        return maxUsers;
    }
    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }
    public int getMaxVeterinarians() {
        return maxVeterinarians;
    }
    public void setMaxVeterinarians(int maxVeterinarians) {
        this.maxVeterinarians = maxVeterinarians;
    }
    public int getMaxBranches() {
        return maxBranches;
    }
    public void setMaxBranches(int maxBranches) {
        this.maxBranches = maxBranches;
    }
    public int getMaxStorageMb() {
        return maxStorageMb;
    }
    public void setMaxStorageMb(int maxStorageMb) {
        this.maxStorageMb = maxStorageMb;
    }
    public int getMaxMessagesMonth() {
        return maxMessagesMonth;
    }
    public void setMaxMessagesMonth(int maxMessagesMonth) {
        this.maxMessagesMonth = maxMessagesMonth;
    }
    public boolean isReportsEnabled() {
        return reportsEnabled;
    }
    public void setReportsEnabled(boolean reportsEnabled) {
        this.reportsEnabled = reportsEnabled;
    }
    public boolean isMessagingEnabled() {
        return messagingEnabled;
    }
    public void setMessagingEnabled(boolean messagingEnabled) {
        this.messagingEnabled = messagingEnabled;
    }
    public boolean isLaboratoryEnabled() {
        return laboratoryEnabled;
    }
    public void setLaboratoryEnabled(boolean laboratoryEnabled) {
        this.laboratoryEnabled = laboratoryEnabled;
    }
    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }
    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
