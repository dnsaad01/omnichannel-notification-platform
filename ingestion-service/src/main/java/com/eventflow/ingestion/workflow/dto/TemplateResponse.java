package com.eventflow.ingestion.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
  private Long id;
  private String name;
  private String channel;
  private String subject;
  private String body;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
