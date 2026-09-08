package com.eventflow.ingestion.service;

import com.eventflow.ingestion.dto.InfrastructureHealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * checkDatabase() and checkRedis() are exercised through their real
 * interfaces (DataSource/Connection, RedisConnectionFactory/RedisConnection)
 * — both mock cleanly. checkKafka() builds a real
 * org.apache.kafka.clients.admin.AdminClient internally rather than taking
 * one through an injectable seam, so there's no clean way to fake a
 * "cluster reachable" response without either spinning up a real broker or
 * statically mocking the Kafka admin client — neither of which this
 * project's other tests do. Its one deterministic, network-free branch
 * (getConfigurationProperties() itself failing before any AdminClient is
 * even created) is covered instead, which still exercises checkKafka's own
 * exception handling and rootCauseMessage unwrapping, and — combined with a
 * failing checkKafka — the overall `healthy` aggregation in getHealth().
 */
@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

  @Mock
  private DataSource dataSource;

  @Mock
  private Connection connection;

  @Mock
  private RedisConnectionFactory redisConnectionFactory;

  @Mock
  private RedisConnection redisConnection;

  @Mock
  private KafkaAdmin kafkaAdmin;

  private MonitoringService monitoringService;

  @BeforeEach
  void setUp() {
    monitoringService = new MonitoringService(dataSource, redisConnectionFactory, kafkaAdmin);
  }

  @Test
  void getHealthShouldReportEverythingUpWhenAllThreeDependenciesAreReachable() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(true);
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("PONG");
    // Deliberately omits "bootstrap.servers" so AdminClient.create(...) fails
    // fast with a config-validation error (no network attempt, no timeout
    // wait) rather than actually trying to reach a broker — see class doc
    // comment on why checkKafka's "up" branch isn't exercised here.
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertTrue(response.getDatabase().isUp());
    assertEquals("Operational (PostgreSQL)", response.getDatabase().getMessage());
    assertTrue(response.getRedis().isUp());
    assertEquals("Connected", response.getRedis().getMessage());
    // Kafka is intentionally left DOWN here (see class doc comment) — this
    // asserts on that rather than pretending otherwise, so `healthy` is
    // correctly false even though the other two dependencies are up.
    assertFalse(response.getKafka().isUp());
    assertFalse(response.isHealthy());
  }

  @Test
  void getHealthShouldReportDatabaseDownWhenTheConnectionDoesNotValidate() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(false);
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("PONG");
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertFalse(response.getDatabase().isUp());
    assertEquals("DOWN: Connection did not validate", response.getDatabase().getMessage());
    assertFalse(response.isHealthy());
  }

  @Test
  void getHealthShouldReportDatabaseDownAndUnwrapTheRootCauseWhenGetConnectionThrows() throws SQLException {
    when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("PONG");
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertFalse(response.getDatabase().isUp());
    assertEquals("DOWN: Connection refused", response.getDatabase().getMessage());
  }

  @Test
  void getHealthShouldReportRedisDownForAnUnexpectedPingResponse() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(true);
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("WEIRD");
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertFalse(response.getRedis().isUp());
    assertEquals("DOWN: Unexpected PING response: WEIRD", response.getRedis().getMessage());
  }

  @Test
  void getHealthShouldReportRedisDownWhenGetConnectionThrows() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(true);
    when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Redis unreachable"));
    when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertFalse(response.getRedis().isUp());
    assertEquals("DOWN: Redis unreachable", response.getRedis().getMessage());
  }

  @Test
  void getHealthShouldReportKafkaDownAndUnwrapTheRootCauseWhenReadingItsConfigurationFails() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.isValid(3)).thenReturn(true);
    when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
    when(redisConnection.ping()).thenReturn("PONG");
    when(kafkaAdmin.getConfigurationProperties()).thenThrow(new RuntimeException("Kafka admin misconfigured"));

    InfrastructureHealthResponse response = monitoringService.getHealth();

    assertFalse(response.getKafka().isUp());
    assertEquals("DOWN: Kafka admin misconfigured", response.getKafka().getMessage());
    assertFalse(response.isHealthy());
  }
}
