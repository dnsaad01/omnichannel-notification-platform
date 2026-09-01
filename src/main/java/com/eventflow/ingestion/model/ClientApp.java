package com.eventflow.ingestion.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "client_apps")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String clientId;

    @Column(nullable = false, unique = true)
    private String apiKey;

    private int rateLimitPerMinute;
}
