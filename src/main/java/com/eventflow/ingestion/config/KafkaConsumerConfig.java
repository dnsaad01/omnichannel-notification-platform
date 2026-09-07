package com.eventflow.ingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> kafkaOperations) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
      (record, ex) -> {
        log.error("Dispatching failed event to DLQ: [Topic: {}, Key: {}]. Reason: {}",
          record.topic(), record.key(), ex.getMessage());
        return new TopicPartition(KafkaTopicConfig.TOPIC_DLQ, record.partition());
      });

    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxElapsedTime(10000L);
    backOff.setMaxInterval(4000L);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

    return errorHandler;
  }
}
