package com.eventflow.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling added for the Workflow Engine's wait-resume job
 *  (WorkflowWaitScheduler, Phase 1) — nothing else in the app used
 *  @Scheduled before this. */
@EnableScheduling
@SpringBootApplication
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }

}
