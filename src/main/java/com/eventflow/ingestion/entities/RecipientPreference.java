package com.eventflow.ingestion.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalTime;

@Entity
@Table(name = "recipient_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String recipientId;

    @Builder.Default
    private boolean emailEnabled = true;

    @Builder.Default
    private boolean smsEnabled = true;

    @Builder.Default
    private boolean pushEnabled = true;

    @Builder.Default
    private boolean whatsappEnabled = true;

    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
}