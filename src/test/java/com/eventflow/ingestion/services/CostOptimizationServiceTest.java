package com.eventflow.ingestion.services;

import com.eventflow.ingestion.dtos.CostEvaluationResponse;
import com.eventflow.ingestion.entities.ChannelMetric;
import com.eventflow.ingestion.entities.RecipientPreference;
import com.eventflow.ingestion.repository.ChannelMetricRepository;
import com.eventflow.ingestion.service.CostOptimizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostOptimizationServiceTest {

    @Mock
    private ChannelMetricRepository metricRepository;

    @InjectMocks
    private CostOptimizationServiceImpl costOptimizationService;

    private RecipientPreference mockPreference;
    private ChannelMetric emailMetric;
    private ChannelMetric smsMetric;
    private ChannelMetric pushMetric;

    @BeforeEach
    void setUp() {
        mockPreference = new RecipientPreference();
        mockPreference.setRecipientId("usr_1001");
        mockPreference.setEmailEnabled(true);
        mockPreference.setSmsEnabled(true);
        mockPreference.setPushEnabled(true);
        mockPreference.setWhatsappEnabled(false);
        mockPreference.setQuietHoursStart(LocalTime.of(22, 0));
        mockPreference.setQuietHoursEnd(LocalTime.of(7, 0));

        emailMetric = ChannelMetric.builder()
                .id(1L)
                .channelType("EMAIL")
                .baseCostPerUnit(0.001)
                .totalSent(1000L)
                .totalDelivered(985L)
                .totalFailed(15L)
                .successRate(98.5)
                .build();

        smsMetric = ChannelMetric.builder()
                .id(2L)
                .channelType("SMS")
                .baseCostPerUnit(0.05)
                .totalSent(1000L)
                .totalDelivered(991L)
                .totalFailed(9L)
                .successRate(99.1)
                .build();

        pushMetric = ChannelMetric.builder()
                .id(3L)
                .channelType("PUSH")
                .baseCostPerUnit(0.0)
                .totalSent(1000L)
                .totalDelivered(995L)
                .totalFailed(5L)
                .successRate(99.5)
                .build();
    }

    @Test
    @DisplayName("Should evaluate optimal channel for NORMAL priority")
    void testEvaluateOptimalChannel_NormalPriority() {
        when(metricRepository.findAll()).thenReturn(List.of(smsMetric, emailMetric, pushMetric));

        CostEvaluationResponse response = costOptimizationService.evaluateOptimalChannel(mockPreference, "NORMAL");

        assertNotNull(response);
        assertNotNull(response.getRecommendedChannel());
        assertTrue(response.getEstimatedCost() >= 0.0);
        assertTrue(response.getChannelSuccessRate() > 0.0);
    }

    @Test
    @DisplayName("Should select carrier direct SMS for HIGH priority routing")
    void testEvaluateOptimalChannel_HighPriority() {
        when(metricRepository.findAll()).thenReturn(List.of(emailMetric, smsMetric, pushMetric));

        CostEvaluationResponse response = costOptimizationService.evaluateOptimalChannel(mockPreference, "HIGH");

        assertNotNull(response);
        assertEquals("SMS", response.getRecommendedChannel());
        assertEquals(0.05, response.getEstimatedCost());
    }
}
