package com.eventflow.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  public static final String TOPIC_EMAIL_HIGH = "notification.email.high";
  public static final String TOPIC_EMAIL_LOW = "notification.email.low";
  public static final String TOPIC_SMS_HIGH = "notification.sms.high";
  public static final String TOPIC_SMS_LOW = "notification.sms.low";
  public static final String TOPIC_PUSH_HIGH = "notification.push.high";
  public static final String TOPIC_PUSH_LOW = "notification.push.low";
  public static final String TOPIC_RETRY = "notification-retry";
  public static final String TOPIC_DLQ = "notification-dlq";

  @Bean
  public NewTopic emailHighTopic() {
    return TopicBuilder.name(TOPIC_EMAIL_HIGH).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic emailLowTopic() {
    return TopicBuilder.name(TOPIC_EMAIL_LOW).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic smsHighTopic() {
    return TopicBuilder.name(TOPIC_SMS_HIGH).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic smsLowTopic() {
    return TopicBuilder.name(TOPIC_SMS_LOW).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic pushHighTopic() {
    return TopicBuilder.name(TOPIC_PUSH_HIGH).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic pushLowTopic() {
    return TopicBuilder.name(TOPIC_PUSH_LOW).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic retryTopic() {
    return TopicBuilder.name(TOPIC_RETRY).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic dlqTopic() {
    return TopicBuilder.name(TOPIC_DLQ).partitions(3).replicas(1).build();
  }
}
