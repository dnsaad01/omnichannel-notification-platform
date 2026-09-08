package com.eventflow.ingestion.exception;

import com.eventflow.ingestion.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests of every @ExceptionHandler method — no MockMvc, no
 * Spring context. The controller tests (TemplateControllerTest,
 * WorkflowControllerTest, etc.) already prove this class is actually wired
 * up via @RestControllerAdvice for a couple of these; this class instead
 * proves each handler method's own mapping logic (status code, body shape)
 * in isolation, including a few exception types no controller test happens
 * to trigger (UnauthorizedException, RateLimitExceededException, the
 * generic Exception fallback).
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  @Mock
  private HttpServletRequest request;

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    when(request.getRequestURI()).thenReturn("/api/some/path");
  }

  @Test
  void shouldMapUnauthorizedExceptionTo401() {
    ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedException(
      new UnauthorizedException("Missing API Key"), request);

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals(401, response.getBody().getStatus());
    assertEquals("Unauthorized", response.getBody().getError());
    assertEquals("Missing API Key", response.getBody().getMessage());
    assertEquals("/api/some/path", response.getBody().getPath());
  }

  @Test
  void shouldMapRateLimitExceededExceptionTo429() {
    ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceededException(
      new RateLimitExceededException("Rate limit exceeded for client: acme"), request);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    assertEquals(429, response.getBody().getStatus());
    assertEquals("Rate limit exceeded for client: acme", response.getBody().getMessage());
  }

  @Test
  void shouldMapTemplateNotFoundExceptionTo404WithItsOwnFormattedMessage() {
    ResponseEntity<ErrorResponse> response = handler.handleTemplateNotFoundException(
      new TemplateNotFoundException(404L), request);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Template not found: 404", response.getBody().getMessage());
  }

  @Test
  void shouldMapWorkflowNotFoundExceptionTo404() {
    ResponseEntity<ErrorResponse> response = handler.handleWorkflowNotFoundException(
      new WorkflowNotFoundException(7L), request);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Workflow not found: 7", response.getBody().getMessage());
  }

  @Test
  void shouldMapWorkflowExecutionNotFoundExceptionTo404() {
    ResponseEntity<ErrorResponse> response = handler.handleWorkflowExecutionNotFoundException(
      new WorkflowExecutionNotFoundException(99L), request);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Workflow execution not found: 99", response.getBody().getMessage());
  }

  @Test
  void shouldMapWorkflowValidationExceptionTo400AndJoinEveryError() {
    ResponseEntity<ErrorResponse> response = handler.handleWorkflowValidationException(
      new WorkflowValidationException(List.of("no TRIGGER node", "dangling edge [e1]")), request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("no TRIGGER node | dangling edge [e1]", response.getBody().getMessage());
  }

  @Test
  void shouldMapMethodArgumentNotValidExceptionTo400AndJoinFieldErrorMessages() {
    FieldError nameError = new FieldError("workflowRequest", "name", "Workflow name is required");
    FieldError triggerError = new FieldError("workflowRequest", "triggerEventType", "triggerEventType is required");
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.getFieldErrors()).thenReturn(List.of(nameError, triggerError));
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Validation failed: Workflow name is required, triggerEventType is required",
      response.getBody().getMessage());
  }

  @Test
  void shouldMapAnyOtherExceptionTo500WithItsOwnMessage() {
    ResponseEntity<ErrorResponse> response = handler.handleGenericException(
      new IllegalStateException("something unexpected broke"), request);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("something unexpected broke", response.getBody().getMessage());
  }

  @Test
  void shouldFallBackToAGenericMessageWhenTheExceptionHasNoMessageOfItsOwn() {
    ResponseEntity<ErrorResponse> response = handler.handleGenericException(new NullPointerException(), request);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("An unexpected error occurred", response.getBody().getMessage());
  }
}
