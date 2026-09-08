package com.eventflow.ingestion.workflow.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextUtils is pure, stateless static logic with no Spring/Mockito
 * needed — it's already exercised indirectly through GatewayNodeHandlerTest,
 * NotificationNodeHandlerTest and WorkflowExecutionEngineTest, but none of
 * those drive its null-handling and multi-placeholder edge cases directly,
 * so this fills in the branches those handler-level tests never happen to
 * reach (a null/blank context or path, a path through a non-Map value, a
 * template with more than one placeholder, an unresolved placeholder).
 */
class ContextUtilsTest {

  // --------------------------------------------------------------- resolve

  @Test
  void resolveShouldReturnNullForANullContext() {
    assertNull(ContextUtils.resolve(null, "user.email"));
  }

  @Test
  void resolveShouldReturnNullForANullOrBlankPath() {
    Map<String, Object> context = Map.of("user", Map.of("email", "a@example.com"));

    assertNull(ContextUtils.resolve(context, null));
    assertNull(ContextUtils.resolve(context, "  "));
  }

  @Test
  void resolveShouldWalkNestedMapsForAMultiSegmentDotPath() {
    Map<String, Object> context = Map.of("user", Map.of("profile", Map.of("firstName", "Alex")));

    assertEquals("Alex", ContextUtils.resolve(context, "user.profile.firstName"));
  }

  @Test
  void resolveShouldReturnNullWhenAnIntermediateSegmentIsMissing() {
    Map<String, Object> context = Map.of("user", Map.of("email", "a@example.com"));

    assertNull(ContextUtils.resolve(context, "user.profile.firstName"));
  }

  @Test
  void resolveShouldReturnNullWhenAnIntermediateSegmentIsNotAMap() {
    Map<String, Object> context = Map.of("cartValue", 89.9);

    assertNull(ContextUtils.resolve(context, "cartValue.subfield"));
  }

  @Test
  void resolveShouldReturnTheTopLevelValueForASingleSegmentPath() {
    Map<String, Object> context = Map.of("email", "a@example.com");

    assertEquals("a@example.com", ContextUtils.resolve(context, "email"));
  }

  // -------------------------------------------------------------------- set

  @Test
  void setShouldCreateIntermediateMapsAsNeeded() {
    Map<String, Object> context = new HashMap<>();

    ContextUtils.set(context, "email.sent", true);

    assertEquals(Map.of("sent", true), context.get("email"));
  }

  @Test
  void setShouldOverwriteANonMapValueAtAnIntermediateSegment() {
    Map<String, Object> context = new HashMap<>();
    context.put("email", "user@example.com");

    ContextUtils.set(context, "email.sent", true);

    assertTrue(context.get("email") instanceof Map);
    assertEquals(true, ((Map<?, ?>) context.get("email")).get("sent"));
  }

  @Test
  void setShouldOverwriteAnExistingLeafValue() {
    Map<String, Object> context = new HashMap<>();
    context.put("status", "PENDING");

    ContextUtils.set(context, "status", "DONE");

    assertEquals("DONE", context.get("status"));
  }

  @Test
  void setShouldWorkForASingleSegmentPath() {
    Map<String, Object> context = new HashMap<>();

    ContextUtils.set(context, "opened", true);

    assertEquals(true, context.get("opened"));
  }

  // ----------------------------------------------------------- interpolate

  @Test
  void interpolateShouldReturnEmptyStringForANullTemplate() {
    assertEquals("", ContextUtils.interpolate(null, Map.of()));
  }

  @Test
  void interpolateShouldReturnTheTemplateUnchangedWhenItHasNoPlaceholders() {
    assertEquals("Hello there", ContextUtils.interpolate("Hello there", Map.of("user", "Alex")));
  }

  @Test
  void interpolateShouldReplaceASinglePlaceholder() {
    Map<String, Object> context = Map.of("user", Map.of("firstName", "Alex"));

    assertEquals("Bonjour Alex", ContextUtils.interpolate("Bonjour {{user.firstName}}", context));
  }

  @Test
  void interpolateShouldReplaceMultiplePlaceholdersInOnePass() {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("user", Map.of("firstName", "Alex"));
    context.put("cartValue", 89.9);

    String result = ContextUtils.interpolate("Hi {{user.firstName}}, your cart total is {{cartValue}} USD", context);

    assertEquals("Hi Alex, your cart total is 89.9 USD", result);
  }

  @Test
  void interpolateShouldReplaceAnUnresolvedPlaceholderWithAnEmptyString() {
    assertEquals("Hello , welcome", ContextUtils.interpolate("Hello {{user.firstName}}, welcome", Map.of()));
  }

  @Test
  void interpolateShouldTolerateWhitespaceInsideThePlaceholderBraces() {
    Map<String, Object> context = Map.of("cartValue", 42);

    assertEquals("Total: 42", ContextUtils.interpolate("Total: {{ cartValue }}", context));
  }

  // ------------------------------------------------------------ newContext

  @Test
  void newContextShouldReturnAnEmptyMutableMap() {
    Map<String, Object> context = ContextUtils.newContext();

    assertTrue(context.isEmpty());
    context.put("k", "v");
    assertEquals("v", context.get("k"));
  }
}
