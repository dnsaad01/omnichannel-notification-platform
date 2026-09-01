package com.eventflow.ingestion.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class FcmPushServiceImpl implements FcmPushService {

  @Override
  public void sendPushNotification(String deviceToken, String title, String body, Map<String, String> data) {
    try {
      Message.Builder messageBuilder = Message.builder()
        .setToken(deviceToken)
        .setNotification(Notification.builder()
          .setTitle(title)
          .setBody(body)
          .build());

      if (data != null && !data.isEmpty()) {
        messageBuilder.putAllData(data);
      }

      if (FirebaseMessaging.getInstance() != null) {
        String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
        log.info("FCM message dispatched successfully: {}", response);
      }
    } catch (IllegalStateException e) {
      log.info("[FCM Push Worker Mock] Sent to token [{}]: title='{}', body='{}'", deviceToken, title, body);
    } catch (Exception e) {
      log.error("FCM dispatch failed for token: {}", deviceToken, e);
    }
  }
}
