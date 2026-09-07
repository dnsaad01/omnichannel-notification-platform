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
  public static final String TOPIC_INGESTION = "notification.ingestion";
  public static final String TOPIC_RETRY = "notification-retry";
  public static final String TOPIC_DLQ = "notification-dlq";

  /** Business events published by the Kafka Event Simulator (or any external
   *  system) — consumed only by WorkflowTriggerConsumer. Distinct from the
   *  low-level per-channel topics above: nothing here reaches a channel
   *  consumer directly, it only ever spawns a WorkflowExecution. */
  public static final String TOPIC_BUSINESS_EVENTS = "notification.events";

  /** Internal control-loop topic: "this execution needs to move forward,"
   *  published by the trigger consumer (fresh match) and the wait-resume
   *  scheduler (timer expired). See WorkflowAdvanceConsumer. */
  public static final String TOPIC_WORKFLOW_ADVANCE = "workflow.execution.advance";

  @Bean
  public NewTopic ingestionTopic() {
    return TopicBuilder.name(TOPIC_INGESTION).partitions(3).replicas(1).build();
  }

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

  @Bean
  public NewTopic businessEventsTopic() {
    return TopicBuilder.name(TOPIC_BUSINESS_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic workflowAdvanceTopic() {
    return TopicBuilder.name(TOPIC_WORKFLOW_ADVANCE).partitions(3).replicas(1).build();
  }
}
