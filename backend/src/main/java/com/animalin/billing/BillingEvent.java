package com.animalin.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "billing_events")
public class BillingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paddle_event_id", nullable = false, unique = true, length = 80)
    private String paddleEventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = SubscriptionStatuses.EVENT_RECEIVED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "error_message")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        if (processingStatus == null) {
            processingStatus = SubscriptionStatuses.EVENT_RECEIVED;
        }
    }

    public boolean isProcessed() {
        return SubscriptionStatuses.EVENT_PROCESSED.equals(processingStatus);
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getPaddleEventId() {
        return paddleEventId;
    }
    public void setPaddleEventId(String paddleEventId) {
        this.paddleEventId = paddleEventId;
    }
    public String getEventType() {
        return eventType;
    }
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    public Instant getOccurredAt() {
        return occurredAt;
    }
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
    public Instant getReceivedAt() {
        return receivedAt;
    }
    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
    public Instant getProcessedAt() {
        return processedAt;
    }
    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
    public String getProcessingStatus() {
        return processingStatus;
    }
    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }
    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
