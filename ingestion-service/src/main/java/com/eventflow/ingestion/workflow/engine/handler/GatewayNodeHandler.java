package com.eventflow.ingestion.workflow.engine.handler;

import com.eventflow.ingestion.workflow.engine.ContextUtils;
import com.eventflow.ingestion.workflow.engine.graph.NodeDef;
import com.eventflow.ingestion.workflow.model.WorkflowExecution;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Executes a GATEWAY (conditional branch) node. Not a suspend point — it
 * evaluates immediately against whatever the context holds *right now* and
 * continues the loop down the "yes" or "no" edge. If a workflow
 * needs to branch on something that only becomes true later (e.g.
 * email.opened), a WAIT node upstream of the GATEWAY is what creates that
 * delay — the GATEWAY itself never waits.
 *
 * Expected node config:
 * { "variable": "email.opened", "operator": "equals", "value": "true" }
 * operators: equals | notEquals | greaterThan | lessThan | exists
 */
@Component
public class GatewayNodeHandler implements NodeHandler {

  @Override
  public String nodeType() {
    return "GATEWAY";
  }

  @Override
  public NodeOutcome handle(WorkflowExecution execution, NodeDef node, Map<String, Object> context) {
    Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();
    String variable = config.get("variable") != null ? String.valueOf(config.get("variable")) : null;
    String operator = config.get("operator") != null ? String.valueOf(config.get("operator")) : "equals";
    Object expected = config.get("value");

    if (variable == null) {
      return NodeOutcome.fail("GATEWAY node [" + node.getId() + "] has no variable configured");
    }

    Object actual = ContextUtils.resolve(context, variable);
    boolean matched = evaluate(operator, actual, expected);
    String branch = matched ? "yes" : "no";

    String logMessage = "Condition [" + variable + " " + operator + " " + expected + "] → "
      + (matched ? "OUI" : "NON") + " (valeur observée : " + actual + ")";
    return NodeOutcome.continueBranch(branch, logMessage);
  }

  private boolean evaluate(String operator, Object actual, Object expected) {
    return switch (operator) {
      case "exists" -> actual != null;
      case "equals" -> looseEquals(actual, expected);
      case "notEquals" -> !looseEquals(actual, expected);
      case "greaterThan" -> compareNumeric(actual, expected, c -> c > 0);
      case "lessThan" -> compareNumeric(actual, expected, c -> c < 0);
      default -> throw new IllegalStateException("Unsupported gateway operator: " + operator);
    };
  }

  private boolean looseEquals(Object actual, Object expected) {
    if (actual == null || expected == null) {
      return actual == expected;
    }
    if (actual instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(expected)) || "false".equalsIgnoreCase(String.valueOf(expected))) {
      return Boolean.parseBoolean(String.valueOf(actual)) == Boolean.parseBoolean(String.valueOf(expected));
    }
    if (isNumeric(actual) && isNumeric(expected)) {
      return toDouble(actual) == toDouble(expected);
    }
    return String.valueOf(actual).equals(String.valueOf(expected));
  }

  private boolean compareNumeric(Object actual, Object expected, java.util.function.IntPredicate test) {
    if (!isNumeric(actual) || !isNumeric(expected)) {
      return false;
    }
    return test.test(Double.compare(toDouble(actual), toDouble(expected)));
  }

  private boolean isNumeric(Object value) {
    if (value instanceof Number) {
      return true;
    }
    try {
      Double.parseDouble(String.valueOf(value));
      return true;
    } catch (NumberFormatException | NullPointerException e) {
      return false;
    }
  }

  private double toDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
  }
}
