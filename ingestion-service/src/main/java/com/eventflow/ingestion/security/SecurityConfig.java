package com.eventflow.ingestion.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Endpoints that stay open with NO JWT required — each one has a reason it
   * has to, not just "was already permitAll":
   *
   *  - /api/monitoring/**, /actuator/**  : requested to stay public (infra
   *    probes / uptime checks — nothing a logged-in user should gate).
   *  - /api/v1/notifications/send(-test) : already authenticated by
   *    NotificationIngestionService's own X-API-KEY / client_apps check
   *    (see NotificationController). External systems call this with an API
   *    key, never a user's Bearer token — requiring a JWT here would break
   *    every existing integration that only has an API key. If you want
   *    *both* checks (a logged-in frontend user AND a valid API key), remove
   *    this line instead of adding it.
   *  - /api/tracking/**                  : the email-open tracking pixel
   *    (TrackingController) — fetched anonymously by the recipient's mail
   *    client, which can never present a Bearer token. Securing this would
   *    just make every tracked email show a broken image.
   *  - swagger/api-docs                  : local API exploration only —
   *    tighten or remove before any non-local deployment.
   *
   * Everything else — /api/templates/**, /api/v1/simulator/**,
   * /api/dashboard/**, /api/workflows/**, /api/workflow-executions/**,
   * /api/business-events/** — is what the Angular app calls directly, so
   * those now require a valid Keycloak-issued JWT. Note this does change the
   * documented "exercise the engine with nothing but curl" flow described in
   * BusinessEventController's class comment: a bare curl POST to
   * /api/business-events/publish will now get a 401 unless you attach
   * Authorization: Bearer <token>. Add that path to this list too if you
   * want to keep it open for manual testing.
   */
  private static final String[] PUBLIC_PATTERNS = {
    "/api/monitoring/**",
    "/actuator/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/api/v1/notifications/send",
    "/api/v1/notifications/send-test",
    "/api/tracking/**"
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .cors(Customizer.withDefaults())
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_PATTERNS).permitAll()
        .anyRequest().authenticated()
      )
      // Activates OAuth2ResourceServerAutoConfiguration's JwtDecoder, built
      // from spring.security.oauth2.resourceserver.jwt.jwk-set-uri, which
      // was already sitting in application.properties but never used
      // because nothing called .oauth2ResourceServer() before now. Keycloak
      // (localhost:8081) must be running for token validation to succeed —
      // the decoder fetches/caches signing keys from its JWKS endpoint.
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      );

    return http.build();
  }

  /**
   * Keycloak puts realm roles under the "realm_access.roles" claim, not the
   * "scope"/"scp" claim Spring Security's default JwtGrantedAuthoritiesConverter
   * looks for. Without this override, every request still authenticates fine
   * (the signature/expiry check doesn't need it) but ends up with zero
   * granted authorities — so any future .hasRole("ADMIN") rule would 403
   * everyone, including real admins. Maps the two realm roles defined in
   * realm-export.json (ADMIN, SERVICE_CLIENT) to ROLE_ADMIN / ROLE_SERVICE_CLIENT.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Map<String, Object> realmAccess = jwt.getClaim("realm_access");
      if (realmAccess == null || realmAccess.get("roles") == null) {
        return List.of();
      }

      @SuppressWarnings("unchecked")
      Collection<String> roles = (Collection<String>) realmAccess.get("roles");

      return roles.stream()
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .collect(Collectors.toList());
    });
    return converter;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of("*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
