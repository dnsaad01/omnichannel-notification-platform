package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.entities.RecipientPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecipientPreferenceRepository extends JpaRepository<RecipientPreference, Long> {
    Optional<RecipientPreference> findByRecipientId(String recipientId);
}
