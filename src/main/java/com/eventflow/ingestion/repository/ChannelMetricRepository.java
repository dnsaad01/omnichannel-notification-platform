package com.eventflow.ingestion.repository;

import com.eventflow.ingestion.entities.ChannelMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelMetricRepository extends JpaRepository<ChannelMetric, Long> {
    Optional<ChannelMetric> findByChannelTypeIgnoreCase(String channelType);
}
