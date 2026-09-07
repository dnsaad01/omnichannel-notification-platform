package com.eventflow.ingestion.workflow.engine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dot-path get/set and {{variable}} interpolation over an execution's
 * context map — the same context_json blob a WorkflowExecution carries,
 * deserialized to a Map for the duration of one advance() call.
 *
 * Kept as static utility methods (no Spring bean) since it's pure,
 * stateless logic every node handler needs and none of them benefit from
 * DI here.
 */
public final class ContextUtils {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

  private ContextUtils() {
  }

  /** Resolves a dot path like "user.firstName" against nested maps. Returns
   *  null if any segment is missing or the value along the way isn't a Map. */
  @SuppressWarnings("unchecked")
  public static Object resolve(Map<String, Object> context, String path) {
    if (context == null || path == null || path.isBlank()) {
      return null;
    }
    Object current = context;
    for (String segment : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = ((Map<String, Object>) map).get(segment);
    }
    return current;
  }

  /** Sets a dot path, creating intermediate maps as needed — e.g.
   *  set(ctx, "email.sent", true) turns {} into {"email": {"sent": true}}. */
  @SuppressWarnings("unchecked")
  public static void set(Map<String, Object> context, String path, Object value) {
    String[] segments = path.split("\\.");
    Map<String, Object> cursor = context;
    for (int i = 0; i < segments.length - 1; i++) {
      Object next = cursor.get(segments[i]);
      if (!(next instanceof Map)) {
        next = new LinkedHashMap<String, Object>();
        cursor.put(segments[i], next);
      }
      cursor = (Map<String, Object>) next;
    }
    cursor.put(segments[segments.length - 1], value);
  }

  /** Replaces every {{dot.path}} placeholder in `template` with its resolved
   *  value from `context` (empty string if unresolved), so a template body
   *  like "Bonjour {{user.firstName}}" renders against a real execution. */
  public static String interpolate(String template, Map<String, Object> context) {
    if (template == null) {
      return "";
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      Object value = resolve(context, matcher.group(1));
      matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  public static Map<String, Object> newContext() {
    return new HashMap<>();
  }
}
