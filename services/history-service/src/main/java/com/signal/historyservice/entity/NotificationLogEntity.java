package com.signal.historyservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notification_logs", indexes = {
        @Index(name = "idx_notification_logs_dispatched_at", columnList = "dispatched_at DESC"),
        @Index(name = "idx_notification_logs_stock_code", columnList = "stock_code"),
        @Index(name = "idx_notification_logs_status", columnList = "status"),
        @Index(name = "idx_notification_logs_channel", columnList = "channel")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 50)
    private String stockName;

    @Column(name = "signal_types", nullable = false, length = 200)
    private String signalTypes;  // comma-separated

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;  // TELEGRAM | SLACK

    @Column(name = "status", nullable = false, length = 20)
    private String status;  // SENT | SUPPRESSED

    @Column(name = "suppress_reason", length = 255)
    private String suppressReason;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "trace_id", length = 50)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
