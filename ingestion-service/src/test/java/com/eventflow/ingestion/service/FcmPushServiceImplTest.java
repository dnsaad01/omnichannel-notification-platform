package com.eventflow.ingestion.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * No FirebaseApp is ever initialized in this test JVM (nothing in this
 * project's main or test code calls FirebaseApp.initializeApp(...)), so
 * FirebaseMessaging.getInstance() deterministically throws
 * IllegalStateException here — exactly the branch
 * FcmPushServiceImpl#sendPushNotification already catches and treats as
 * "local/dev mock" mode (see its own catch(IllegalStateException) comment).
 * That makes this a genuine, network-free, deterministic unit test of the
 * defensive fallback rather than a test that happens to rely on mocking a
 * third-party SDK's static factory methods.
 */
class FcmPushServiceImplTest {

  private final FcmPushServiceImpl service = new FcmPushServiceImpl();

  @Test
  void sendPushNotificationShouldFallBackToMockLoggingRatherThanThrowWhenNoDataIsGiven() {
    assertDoesNotThrow(() -> service.sendPushNotification("device-token-1", "Title", "Body", null));
  }

  @Test
  void sendPushNotificationShouldFallBackToMockLoggingRatherThanThrowWhenDataIsPresent() {
    assertDoesNotThrow(() ->
      service.sendPushNotification("device-token-1", "Title", "Body", Map.of("cartId", "c1")));
  }

  @Test
  void sendPushNotificationShouldFallBackToMockLoggingForAnEmptyDataMap() {
    assertDoesNotThrow(() -> service.sendPushNotification("device-token-1", "Title", "Body", Map.of()));
  }
}
