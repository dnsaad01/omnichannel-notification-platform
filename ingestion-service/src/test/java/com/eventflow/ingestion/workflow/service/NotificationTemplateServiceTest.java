package com.eventflow.ingestion.workflow.service;

import com.eventflow.ingestion.exception.TemplateNotFoundException;
import com.eventflow.ingestion.workflow.dto.TemplateRequest;
import com.eventflow.ingestion.workflow.dto.TemplateResponse;
import com.eventflow.ingestion.workflow.model.NotificationTemplate;
import com.eventflow.ingestion.workflow.model.TemplateStatus;
import com.eventflow.ingestion.workflow.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTest {

  @Mock
  private NotificationTemplateRepository notificationTemplateRepository;

  private NotificationTemplateService notificationTemplateService;

  @BeforeEach
  void setUp() {
    notificationTemplateService = new NotificationTemplateService(notificationTemplateRepository);
  }

  @Test
  void createShouldUppercaseTheChannelAndUseTheBodyFieldWhenPresent() {
    when(notificationTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TemplateRequest request = TemplateRequest.builder()
      .name("Welcome Email").channel("email").subject("Hi").body("Hello there").build();

    TemplateResponse response = notificationTemplateService.create(request);

    ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
    verify(notificationTemplateRepository).save(captor.capture());
    assertEquals("EMAIL", captor.getValue().getChannel());
    assertEquals("Hello there", captor.getValue().getBody());
    assertEquals(TemplateStatus.ACTIVE, captor.getValue().getStatus());
    assertEquals("EMAIL", response.getChannel());
  }

  @Test
  void createShouldFallBackToTheLegacyContentFieldWhenBodyIsBlank() {
    // See TemplateRequest's class doc comment: the current Angular Templates
    // page still POSTs `content`, not `body`.
    when(notificationTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TemplateRequest request = TemplateRequest.builder()
      .name("Legacy").channel("sms").content("Sent via legacy field").build();

    notificationTemplateService.create(request);

    ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
    verify(notificationTemplateRepository).save(captor.capture());
    assertEquals("Sent via legacy field", captor.getValue().getBody());
  }

  @Test
  void createShouldDefaultAnUnknownStatusStringToActiveRatherThanThrow() {
    when(notificationTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TemplateRequest request = TemplateRequest.builder()
      .name("X").channel("push").body("b").status("not-a-real-status").build();

    notificationTemplateService.create(request);

    ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
    verify(notificationTemplateRepository).save(captor.capture());
    assertEquals(TemplateStatus.ACTIVE, captor.getValue().getStatus());
  }

  @Test
  void findByIdShouldThrowTemplateNotFoundExceptionForAnUnknownId() {
    when(notificationTemplateRepository.findById(404L)).thenReturn(Optional.empty());

    assertThrows(TemplateNotFoundException.class, () -> notificationTemplateService.findById(404L));
  }

  @Test
  void updateShouldOnlyOverwriteStatusWhenANonBlankStatusIsProvided() {
    NotificationTemplate existing = NotificationTemplate.builder()
      .id(1L).name("Old").channel("EMAIL").body("old body").status(TemplateStatus.ACTIVE).build();
    when(notificationTemplateRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(notificationTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TemplateRequest request = TemplateRequest.builder()
      .name("New Name").channel("email").body("new body").status("  ").build();

    TemplateResponse response = notificationTemplateService.update(1L, request);

    assertEquals("New Name", response.getName());
    assertEquals("new body", response.getBody());
    // status left untouched because the incoming status was blank
    assertEquals("ACTIVE", response.getStatus());
  }

  @Test
  void deleteShouldThrowTemplateNotFoundExceptionRatherThanCallDeleteWhenTheIdDoesNotExist() {
    when(notificationTemplateRepository.existsById(404L)).thenReturn(false);

    assertThrows(TemplateNotFoundException.class, () -> notificationTemplateService.delete(404L));

    verify(notificationTemplateRepository, never()).deleteById(any());
  }

  @Test
  void deleteShouldCallRepositoryDeleteWhenTheIdExists() {
    when(notificationTemplateRepository.existsById(1L)).thenReturn(true);

    notificationTemplateService.delete(1L);

    verify(notificationTemplateRepository, times(1)).deleteById(1L);
  }
}
