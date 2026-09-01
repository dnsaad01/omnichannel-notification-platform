package com.eventflow.ingestion.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TwilioSmsServiceImpl implements TwilioSmsService {

  @Value("${twilio.account-sid:AC_MOCK_SID}")
  private String accountSid;

  @Value("${twilio.auth-token:MOCK_TOKEN}")
  private String authToken;

  @Value("${twilio.phone-number:+15005550006}")
  private String fromPhoneNumber;

  @Override
  public void sendSms(String toPhoneNumber, String messageBody) {
    try {
      if (!accountSid.startsWith("AC_MOCK")) {
        Twilio.init(accountSid, authToken);
        Message message = Message.creator(
          new PhoneNumber(toPhoneNumber),
          new PhoneNumber(fromPhoneNumber),
          messageBody
        ).create();
        log.info("Twilio SMS dispatched with SID: {}", message.getSid());
      } else {
        log.info("[Twilio SMS Worker Mock] Dispatched to [{}] via carrier sender [{}]: '{}'",
          toPhoneNumber, fromPhoneNumber, messageBody);
      }
    } catch (Exception e) {
      log.error("Failed to dispatch SMS via Twilio to {}", toPhoneNumber, e);
    }
  }
}
