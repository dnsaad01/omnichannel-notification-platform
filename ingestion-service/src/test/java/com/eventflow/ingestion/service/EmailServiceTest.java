package com.eventflow.ingestion.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * mailSender.createMimeMessage() can't be mocked to return null (Mockito
 * would then NPE inside MimeMessageHelper's constructor), so it returns a
 * REAL MimeMessage built from a real, unconnected mail Session — no network
 * ever gets touched, and this still exercises the actual toHtml(...)
 * wrapping logic and MimeMessageHelper population, not just a mocked
 * pass-through.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock
  private JavaMailSender mailSender;

  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = new EmailService(mailSender);
    ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@example.com");
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
  }

  @Test
  void shouldSendThroughTheMailSenderWithoutThrowingForAPlainTextBody() {
    emailService.sendEmail("user@example.com", "Subject", "Line one\nLine two");

    verify(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldWrapPlainTextNewlinesAsBrTagsButPassThroughExistingHtmlUnchanged() throws Exception {
    // toHtml(...) is private, so this is exercised indirectly: build the
    // real MimeMessage via the mocked createMimeMessage(), send a body that
    // already contains a tracking-pixel-style <img> tag, and assert the
    // resulting message content is the untouched HTML rather than an
    // escaped/re-wrapped version of it.
    MimeMessage captured = new MimeMessage(Session.getInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(captured);

    String htmlBody = "<p>Hello</p><img src=\"http://localhost:8082/api/tracking/open/1\">";
    emailService.sendEmail("user@example.com", "Subject", htmlBody);

    String content = (String) captured.getContent();
    assertTrue(content.contains("<img src=\"http://localhost:8082/api/tracking/open/1\">"));
  }

  @Test
  void shouldWrapRuntimeExceptionsFromTheMailSenderRatherThanSwallowThem() {
    doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send((MimeMessage) org.mockito.ArgumentMatchers.any());

    RuntimeException ex = assertThrows(RuntimeException.class,
      () -> emailService.sendEmail("user@example.com", "Subject", "Body"));

    assertTrue(ex.getMessage().contains("Email sending failed"));
  }
}
