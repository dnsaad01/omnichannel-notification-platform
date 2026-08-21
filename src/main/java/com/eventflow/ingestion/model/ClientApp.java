package com.eventflow.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_apps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientApp {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "client_name", length = 100)
    private String clientName;

    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "rate_limit_quota")
    private Integer rateLimitQuota;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public Integer getRateLimitQuota() {
        return rateLimitQuota;
    }

    public void setRateLimitQuota(Integer rateLimitQuota) {
        this.rateLimitQuota = rateLimitQuota;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
