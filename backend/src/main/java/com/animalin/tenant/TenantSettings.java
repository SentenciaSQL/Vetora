package com.animalin.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_settings")
public class TenantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "date_format", nullable = false)
    private String dateFormat = "dd/MM/yyyy";

    @Column(name = "default_appointment_min", nullable = false)
    private int defaultAppointmentMin = 30;

    @Column(name = "cancellation_hours", nullable = false)
    private int cancellationHours = 12;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @Column(name = "notify_push", nullable = false)
    private boolean notifyPush = true;

    @Column(name = "enabled_locales", nullable = false)
    private String enabledLocales = "es,en";

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Tenant getTenant() {
        return tenant;
    }
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }
    public String getDateFormat() {
        return dateFormat;
    }
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }
    public int getDefaultAppointmentMin() {
        return defaultAppointmentMin;
    }
    public void setDefaultAppointmentMin(int defaultAppointmentMin) {
        this.defaultAppointmentMin = defaultAppointmentMin;
    }
    public int getCancellationHours() {
        return cancellationHours;
    }
    public void setCancellationHours(int cancellationHours) {
        this.cancellationHours = cancellationHours;
    }
    public boolean isNotifyEmail() {
        return notifyEmail;
    }
    public void setNotifyEmail(boolean notifyEmail) {
        this.notifyEmail = notifyEmail;
    }
    public boolean isNotifyPush() {
        return notifyPush;
    }
    public void setNotifyPush(boolean notifyPush) {
        this.notifyPush = notifyPush;
    }
    public String getEnabledLocales() {
        return enabledLocales;
    }
    public void setEnabledLocales(String enabledLocales) {
        this.enabledLocales = enabledLocales;
    }
}
