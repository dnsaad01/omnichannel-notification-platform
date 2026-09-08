package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors the Angular DlqMessage model exactly (models/monitoring.model.ts). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessageResponse {
  private String id;
  private String recipient;
  private String channel;
  private String errorReason;
  private String timestamp;
}
