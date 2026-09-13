package com.eventflow.ingestion.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input DTO for POST/PUT /api/templates.
 *
 * `content` is a legacy alias: the Angular Templates page
 * (pages/templates/templates.component.ts) POSTs
 * { name, channel, content } — there's no `body` field on the wire.
 * The service maps content -> body when body is blank, so the UI keeps
 * working unchanged; the canonical field is `body`. This alias should be
 * dropped once the Templates page no longer needs it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequest {

  @NotBlank(message = "Template name is required")
  private String name;

  @NotBlank(message = "Channel is required")
  private String channel;

  private String subject;

  private String body;

  /** @deprecated legacy alias for `body` — see class-level note. */
  @Deprecated
  private String content;

  private String status;
}
