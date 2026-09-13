package com.eventflow.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling activates the Workflow Engine's wait-resume job
 *  (WorkflowWaitScheduler) — nothing else in the app uses @Scheduled. */
@EnableScheduling
@SpringBootApplication
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }

}
