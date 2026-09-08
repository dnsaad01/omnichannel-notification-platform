package com.eventflow.ingestion.config;

import com.eventflow.ingestion.model.ClientApp;
import com.eventflow.ingestion.repository.ClientAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultClientAppSeederTest {

  @Mock
  private ClientAppRepository clientAppRepository;

  private DefaultClientAppSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new DefaultClientAppSeeder(clientAppRepository);
  }

  @Test
  void runShouldSkipSeedingWhenClientAppsAlreadyHasData() throws Exception {
    when(clientAppRepository.count()).thenReturn(3L);

    seeder.run();

    verify(clientAppRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void runShouldInsertExactlyOneDefaultDevClientWhenTheTableIsEmpty() throws Exception {
    when(clientAppRepository.count()).thenReturn(0L);

    seeder.run();

    ArgumentCaptor<ClientApp> captor = ArgumentCaptor.forClass(ClientApp.class);
    verify(clientAppRepository).save(captor.capture());
    ClientApp saved = captor.getValue();
    assertEquals("frontend-app", saved.getClientId());
    assertEquals("my-secret-key-123", saved.getApiKey());
    assertEquals(100, saved.getRateLimitPerMinute());
  }
}
