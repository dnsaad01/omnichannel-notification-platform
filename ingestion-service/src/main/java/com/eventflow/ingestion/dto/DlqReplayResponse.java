package com.eventflow.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors the Angular DlqReplayResponse model (models/monitoring.model.ts). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqReplayResponse {
  private int replayedCount;
}
