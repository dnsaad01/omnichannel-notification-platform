package com.eventflow.ingestion.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Deliberately does not exercise the real Twilio.init(...) / Message.creator(...)
 * .create() branch: that would need either a live network call to Twilio's
 * real API (slow, flaky in CI, and a genuinely bad practice to run from a
 * unit test) or mocking Twilio's static SDK methods, which this project's
 * other tests don't do anywhere. Both of the deterministic, network-free
 * branches sendSms actually has are covered instead: the local/dev "mock
 * carrier" path or account-sid check, and the resilience of its own
 * try/catch when the @Value fields aren't populated (no Spring context in a
 * plain unit test) — either way sendSms must never throw back to its caller.
 */
class TwilioSmsServiceImplTest {

  private final TwilioSmsServiceImpl service = new TwilioSmsServiceImpl();

  @Test
  void sendSmsShouldLogViaTheMockCarrierPathAndNeverThrowWhenTheAccountSidIsTheDevMockValue() {
    ReflectionTestUtils.setField(service, "accountSid", "AC_MOCK_SID");
    ReflectionTestUtils.setField(service, "authToken", "MOCK_TOKEN");
    ReflectionTestUtils.setField(service, "fromPhoneNumber", "+15005550006");

    assertDoesNotThrow(() -> service.sendSms("+15550001111", "Your code is 1234"));
  }

  @Test
  void sendSmsShouldSwallowAnyExceptionRatherThanPropagateItToItsCaller() {
    // accountSid/authToken/fromPhoneNumber are left unset (null) here, as
    // they would be for any @Value field outside a real Spring context —
    // sendSms's own try/catch must still prevent that from ever reaching
    // the Kafka consumer thread that calls it.
    assertDoesNotThrow(() -> service.sendSms("+15550001111", "Your code is 1234"));
  }
}
