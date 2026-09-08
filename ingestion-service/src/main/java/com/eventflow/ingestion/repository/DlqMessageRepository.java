package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.model.DlqMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

  List<DlqMessage> findAllByOrderByCreatedAtDesc();
}
