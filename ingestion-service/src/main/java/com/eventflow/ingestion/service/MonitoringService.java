package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.InfrastructureHealthResponse;
import com.eventflow.ingestion.dto.ServiceHealthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.Node;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Real, live connectivity checks for the Monitoring page (MonitoringController,
 * GET /api/monitoring/health).
 *
 * ⚠️ Root cause of the reported bug — READ BEFORE ASSUMING THIS FIXES A
 * REGRESSION IN EXISTING LOGIC
 * There was no health-check logic anywhere to "not detect" the outage:
 * MonitoringComponent's `systemHealth` (kafkaStatus/redisStatus/dbStatus)
 * was three hardcoded string literals, never fetched from any backend call
 * — and the "● Infrastructure Stable" badge in the template was a static
 * `<span>`, unconditionally green, with no binding to systemHealth or
 * anything else at all. Stopping the Postgres container couldn't possibly
 * change what the page showed, because nothing on the page was ever wired
 * to Postgres (or Kafka, or Redis) to begin with — same pattern as the
 * Statistics and Notifications pages had before those were wired to real
 * data in this same project.
 *
 * Each check below opens a real connection with its own short timeout and
 * reports UP only on actual success — no caching, no relying on a
 * connection pool's last-known-good state — so stopping a container is
 * reflected on the very next GET /api/monitoring/health, typically within
 * the timeout window below rather than instantly (a dead TCP peer doesn't
 * always fail fast; these timeouts exist specifically so this endpoint
 * itself can't hang indefinitely on a dead dependency).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

  private static final int DB_TIMEOUT_SECONDS = 3;
  private static final int KAFKA_TIMEOUT_SECONDS = 3;

  private final DataSource dataSource;
  private final RedisConnectionFactory redisConnectionFactory;
  private final KafkaAdmin kafkaAdmin;

  public InfrastructureHealthResponse getHealth() {
    ServiceHealthStatus database = checkDatabase();
    ServiceHealthStatus kafka = checkKafka();
    ServiceHealthStatus redis = checkRedis();

    return InfrastructureHealthResponse.builder()
      .healthy(database.isUp() && kafka.isUp() && redis.isUp())
      .database(database)
      .kafka(kafka)
      .redis(redis)
      .build();
  }

  /**
   * Connection#isValid(timeout) is JDBC's own built-in liveness check — for
   * PostgreSQL the driver issues a lightweight internal validation, not SQL
   * text this code has to write itself. This is the same technique Spring
   * Boot's own auto-configured DataSourceHealthIndicator uses under the
   * hood for /actuator/health (already enabled here too, since
   * spring-boot-starter-actuator is on the classpath and
   * management.endpoint.health.show-details=always is already set in
   * application.properties — worth comparing against /actuator/health
   * directly). This method exists separately so the Monitoring page gets a
   * response shaped for its own UI instead of the frontend having to parse
   * actuator's nested components.db.status JSON.
   */
  private ServiceHealthStatus checkDatabase() {
    try (Connection connection = dataSource.getConnection()) {
      if (connection.isValid(DB_TIMEOUT_SECONDS)) {
        return ServiceHealthStatus.up("Operational (PostgreSQL)");
      }
      return ServiceHealthStatus.down("Connection did not validate");
    } catch (Exception e) {
      log.warn("Database health check failed: {}", e.getMessage());
      return ServiceHealthStatus.down(rootCauseMessage(e));
    }
  }

  /**
   * Builds a real AdminClient from KafkaAdmin#getConfigurationProperties()
   * — verified against spring-kafka's own source (KafkaAdmin#createAdmin,
   * a private method internal to the class) to confirm this is the exact
   * same construction Spring Kafka itself uses internally for
   * KafkaAdmin#clusterId()/#describeTopics(), not a guess at its API.
   * describeCluster().nodes().get(timeout, ...) blocks — with our own
   * timeout — until the broker actually answers, so a stopped Kafka
   * container fails this exactly like a dead Postgres fails
   * Connection#isValid above, instead of silently succeeding because a
   * bean merely exists.
   */
  private ServiceHealthStatus checkKafka() {
    try (Admin adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Collection<Node> nodes = adminClient.describeCluster().nodes()
        .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return ServiceHealthStatus.up("OK (" + nodes.size() + " broker(s))");
    } catch (Exception e) {
      log.warn("Kafka health check failed: {}", e.getMessage());
      return ServiceHealthStatus.down(rootCauseMessage(e));
    }
  }

  /** RedisConnectionFactory#getConnection().ping() issues a real PING
   *  command to the broker — the standard Redis liveness check. */
  private ServiceHealthStatus checkRedis() {
    try (RedisConnection connection = redisConnectionFactory.getConnection()) {
      String pong = connection.ping();
      if ("PONG".equalsIgnoreCase(pong)) {
        return ServiceHealthStatus.up("Connected");
      }
      return ServiceHealthStatus.down("Unexpected PING response: " + pong);
    } catch (Exception e) {
      log.warn("Redis health check failed: {}", e.getMessage());
      return ServiceHealthStatus.down(rootCauseMessage(e));
    }
  }

  /** Unwraps to the innermost cause so a network-level exception
   *  (ConnectException, TimeoutException, etc.) is what actually reaches
   *  the frontend, rather than a generic wrapper's own message. */
  private String rootCauseMessage(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String msg = cause.getMessage();
    return msg != null && !msg.isBlank() ? msg : cause.getClass().getSimpleName();
  }
}
