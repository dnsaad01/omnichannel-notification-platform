package com.eventflow.ingestion.service;

import java.util.Map;

public interface FcmPushService {
  void sendPushNotification(String deviceToken, String title, String body, Map<String, String> data);
}
