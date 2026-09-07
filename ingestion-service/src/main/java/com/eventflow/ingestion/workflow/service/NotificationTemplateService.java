package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.TemplateNotFoundException;
import com.eventflow.ingestion.workflow.dto.TemplateRequest;
import com.eventflow.ingestion.workflow.dto.TemplateResponse;
import com.eventflow.ingestion.workflow.model.NotificationTemplate;
import com.eventflow.ingestion.workflow.model.TemplateStatus;
import com.eventflow.ingestion.workflow.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

  private final NotificationTemplateRepository notificationTemplateRepository;

  public List<TemplateResponse> findAll() {
    return notificationTemplateRepository.findAll().stream()
      .map(this::toResponse)
      .toList();
  }

  public TemplateResponse findById(Long id) {
    return toResponse(getOrThrow(id));
  }

  public TemplateResponse create(TemplateRequest request) {
    NotificationTemplate template = NotificationTemplate.builder()
      .name(request.getName())
      .channel(request.getChannel().toUpperCase())
      .subject(request.getSubject())
      .body(resolveBody(request))
      .status(resolveStatus(request.getStatus()))
      .build();

    return toResponse(notificationTemplateRepository.save(template));
  }

  public TemplateResponse update(Long id, TemplateRequest request) {
    NotificationTemplate template = getOrThrow(id);
    template.setName(request.getName());
    template.setChannel(request.getChannel().toUpperCase());
    template.setSubject(request.getSubject());
    template.setBody(resolveBody(request));
    if (request.getStatus() != null && !request.getStatus().isBlank()) {
      template.setStatus(resolveStatus(request.getStatus()));
    }

    return toResponse(notificationTemplateRepository.save(template));
  }

  public void delete(Long id) {
    if (!notificationTemplateRepository.existsById(id)) {
      throw new TemplateNotFoundException(id);
    }
    notificationTemplateRepository.deleteById(id);
  }

  private NotificationTemplate getOrThrow(Long id) {
    return notificationTemplateRepository.findById(id)
      .orElseThrow(() -> new TemplateNotFoundException(id));
  }

  /** See TemplateRequest's class-level note: the current Angular Templates
   *  page sends `content`, not `body`, on the wire. */
  private String resolveBody(TemplateRequest request) {
    if (request.getBody() != null && !request.getBody().isBlank()) {
      return request.getBody();
    }
    return request.getContent() != null ? request.getContent() : "";
  }

  private TemplateStatus resolveStatus(String status) {
    if (status == null || status.isBlank()) {
      return TemplateStatus.ACTIVE;
    }
    try {
      return TemplateStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown template status [{}] — defaulting to ACTIVE", status);
      return TemplateStatus.ACTIVE;
    }
  }

  private TemplateResponse toResponse(NotificationTemplate template) {
    return TemplateResponse.builder()
      .id(template.getId())
      .name(template.getName())
      .channel(template.getChannel())
      .subject(template.getSubject())
      .body(template.getBody())
      .status(template.getStatus().name())
      .createdAt(template.getCreatedAt())
      .updatedAt(template.getUpdatedAt())
      .build();
  }
}
