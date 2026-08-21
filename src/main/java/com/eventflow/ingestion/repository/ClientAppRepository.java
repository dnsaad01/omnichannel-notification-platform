package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.model.ClientApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientAppRepository extends JpaRepository<ClientApp, String> {

    Optional<ClientApp> findByApiKeyHash(String apiKeyHash);
}
