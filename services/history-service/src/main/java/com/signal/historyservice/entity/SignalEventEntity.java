package com.signal.historyservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "signal_events", indexes = {
        @Index(name = "idx_signal_events_event_at", columnList = "event_at DESC"),
        @Index(name = "idx_signal_events_stock_code", columnList = "stock_code"),
        @Index(name = "idx_signal_events_status", columnList = "status")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 50)
    private String stockName;

    @Column(name = "signal_types", nullable = false, length = 200)
    private String signalTypes;  // comma-separated: VOLUME_SURGE,GAP_UP

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "status", nullable = false, length = 10)
    private String status;  // DETECTED | REJECTED

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;  // JSON array (detected 시 사유)

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "trace_id", length = 50)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
