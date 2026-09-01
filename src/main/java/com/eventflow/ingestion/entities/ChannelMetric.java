package com.eventflow.ingestion.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "channel_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String channelType;

    @Column(nullable = false)
    private double baseCostPerUnit;

    @Column(nullable = false)
    private long totalSent;

    @Column(nullable = false)
    private long totalDelivered;

    @Column(nullable = false)
    private long totalFailed;

    private double successRate;

    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    public void calculateSuccessRate() {
        if (totalSent > 0) {
            this.successRate = (double) totalDelivered / totalSent * 100.0;
        } else {
            this.successRate = 100.0;
        }
        this.lastUpdated = LocalDateTime.now();
    }
}