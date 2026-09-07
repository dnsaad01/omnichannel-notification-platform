package com.eventflow.ingestion.config;

import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Dev-convenience seeder for the public ingestion API's client-key check.
 *
 * NotificationIngestionService#processAndPublish requires every request to
 * /api/v1/notifications/send to carry an X-API-KEY header that matches a row
 * in client_apps (see ClientAppRepository#findByApiKey) — but nothing in
 * this project ever inserted one: scripts/init.sql is empty and there was
 * no seeder, so client_apps starts empty on any fresh database and every
 * request fails with "Invalid API Key" (401) until a row exists. This class
 * closes that gap: on startup, if client_apps is empty, it inserts exactly
 * one default row so local testing has a working key immediately.
 *
 * ⚠️ Runs unconditionally in every profile — READ BEFORE DEPLOYING
 * This project has no dev/prod profile split (a single application.properties,
 * no application-dev.properties etc.), so there's nothing to gate this
 * against without inventing a profile convention that doesn't otherwise
 * exist here. That means, as written, this also runs against a real/shared
 * database — harmlessly (it only ever inserts when the table is completely
 * empty, so it can't touch or duplicate real client rows once any client
 * exists), but it does mean a fresh production database's very first client
 * would silently be this hardcoded dev key rather than a real one. If/when
 * this codebase gains real environment profiles, gate this behind a
 * dev/local one (e.g. @Profile({"dev","local"})) rather than deleting it —
 * for now, flagging the trade-off is the honest alternative to guessing at
 * a profile setup you haven't established.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultClientAppSeeder implements CommandLineRunner {

  private static final String DEFAULT_CLIENT_ID = "frontend-app";
  private static final String DEFAULT_API_KEY = "my-secret-key-123";
  private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 100;

  private final ClientAppRepository clientAppRepository;

  @Override
  public void run(String... args) {
    if (clientAppRepository.count() > 0) {
      log.debug("client_apps already has data — skipping default dev client seed.");
      return;
    }

    ClientApp defaultClient = ClientApp.builder()
      .clientId(DEFAULT_CLIENT_ID)
      .apiKey(DEFAULT_API_KEY)
      .rateLimitPerMinute(DEFAULT_RATE_LIMIT_PER_MINUTE)
      .build();

    clientAppRepository.save(defaultClient);
    log.warn("client_apps was empty — inserted default DEV client [clientId={}, apiKey={}, rateLimitPerMinute={}]. "
        + "Use this X-API-KEY for local testing against /api/v1/notifications/send. "
        + "Do not rely on this key outside local/dev use.",
      DEFAULT_CLIENT_ID, DEFAULT_API_KEY, DEFAULT_RATE_LIMIT_PER_MINUTE);
  }
}
