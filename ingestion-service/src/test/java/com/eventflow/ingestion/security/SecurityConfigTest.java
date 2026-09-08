package com.eventflow.ingestion.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SecurityConfig's own most important piece of custom logic — mapping
 * Keycloak's "realm_access.roles" claim to Spring Security authorities — is
 * never actually exercised by any @WebMvcTest controller test: those all
 * authenticate via SecurityMockMvcRequestPostProcessors.jwt(), which injects
 * a JwtAuthenticationToken directly into the security context and never
 * calls the real jwtAuthenticationConverter() bean at all (see that
 * method's own doc comment on why it exists in the first place — without
 * it every request would silently end up with zero granted authorities).
 * This drives that converter function directly instead.
 */
class SecurityConfigTest {

  private final SecurityConfig securityConfig = new SecurityConfig();

  private Jwt.Builder aJwt() {
    return Jwt.withTokenValue("token-value")
      .header("alg", "none")
      .issuedAt(Instant.now())
      .expiresAt(Instant.now().plusSeconds(300))
      .subject("user-1");
  }

  @Test
  void shouldMapRealmAccessRolesToRolePrefixedGrantedAuthorities() {
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
    Jwt jwt = aJwt().claim("realm_access", Map.of("roles", List.of("ADMIN", "SERVICE_CLIENT"))).build();

    Collection<? extends GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

    assertEquals(2, authorities.size());
    assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE_CLIENT")));
  }

  @Test
  void shouldReturnNoAuthoritiesWhenTheRealmAccessClaimIsAbsent() {
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
    Jwt jwt = aJwt().build();

    Collection<? extends GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

    assertTrue(authorities.isEmpty());
  }

  @Test
  void shouldReturnNoAuthoritiesWhenRealmAccessHasNoRolesEntry() {
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
    Jwt jwt = aJwt().claim("realm_access", Map.of("otherField", "x")).build();

    Collection<? extends GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

    assertTrue(authorities.isEmpty());
  }

  @Test
  void shouldMapASingleRole() {
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
    Jwt jwt = aJwt().claim("realm_access", Map.of("roles", List.of("ADMIN"))).build();

    Collection<? extends GrantedAuthority> authorities = converter.convert(jwt).getAuthorities();

    assertEquals(List.of("ROLE_ADMIN"), authorities.stream().map(GrantedAuthority::getAuthority).toList());
  }

  @Test
  void corsConfigurationSourceShouldAllowCredentialedRequestsFromAnyOriginPattern() {
    CorsConfigurationSource source = securityConfig.corsConfigurationSource();
    assertTrue(source instanceof UrlBasedCorsConfigurationSource);

    CorsConfiguration configuration = ((UrlBasedCorsConfigurationSource) source)
      .getCorsConfigurations().get("/**");

    assertTrue(configuration.getAllowedOriginPatterns().contains("*"));
    assertTrue(configuration.getAllowedMethods().containsAll(List.of("GET", "POST", "PUT", "DELETE")));
    assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
  }
}
