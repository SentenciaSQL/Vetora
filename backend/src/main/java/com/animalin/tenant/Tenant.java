package com.animalin.tenant;

import com.animalin.common.domain.BaseEntity;
import com.animalin.plan.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "tenants")
@SQLRestriction("deleted = false")
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "commercial_name")
    private String commercialName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "dark_logo_url")
    private String darkLogoUrl;

    @Column(name = "icon_url")
    private String iconUrl;

    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String website;
    private String instagram;
    private String facebook;

    @Column(nullable = false)
    private String timezone = "Europe/Madrid";

    @Column(nullable = false, length = 8)
    private String currency = "EUR";

    @Column(name = "default_locale", nullable = false, length = 8)
    private String defaultLocale = "es";

    @Column(nullable = false, length = 20)
    private String status = "TRIAL";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    public String getSlug() {
        return slug;
    }
    public void setSlug(String slug) {
        this.slug = slug;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getCommercialName() {
        return commercialName;
    }
    public void setCommercialName(String commercialName) {
        this.commercialName = commercialName;
    }
    public String getLogoUrl() {
        return logoUrl;
    }
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
    public String getDarkLogoUrl() {
        return darkLogoUrl;
    }
    public void setDarkLogoUrl(String darkLogoUrl) {
        this.darkLogoUrl = darkLogoUrl;
    }
    public String getIconUrl() {
        return iconUrl;
    }
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public String getWebsite() {
        return website;
    }
    public void setWebsite(String website) {
        this.website = website;
    }
    public String getInstagram() {
        return instagram;
    }
    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }
    public String getFacebook() {
        return facebook;
    }
    public void setFacebook(String facebook) {
        this.facebook = facebook;
    }
    public String getTimezone() {
        return timezone;
    }
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public String getDefaultLocale() {
        return defaultLocale;
    }
    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
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
    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }
    public void setTrialEndsAt(Instant trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }
}
