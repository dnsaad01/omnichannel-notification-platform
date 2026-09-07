package com.eventflow.ingestion.service;

public interface TwilioSmsService {
  void sendSms(String toPhoneNumber, String messageBody);
}
