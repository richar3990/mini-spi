package com.sodep.miniSpi.service;

import com.sodep.miniSpi.spi.SpiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpiRetryServiceTest {
    @Mock
    private SpiClient spiClient;

    private SpiRetryService spiRetryService;

    @BeforeEach
    void setUp() {
        spiRetryService = new SpiRetryService(spiClient);

        ReflectionTestUtils.setField(
                spiRetryService,
                "maxAttempts",
                3
        );

        ReflectionTestUtils.setField(
                spiRetryService,
                "initialDelayMs",
                0L
        );
    }

    @Test
    void processTransfer_shouldReturnTrueWhenSpiSucceedsOnFirstAttempt() {
        when(spiClient.processTransfer(10L))
                .thenReturn(true);

        boolean result = spiRetryService.processTransfer(10L);

        assertTrue(result);

        verify(spiClient, times(1))
                .processTransfer(10L);
    }

    @Test
    void processTransfer_shouldRetryWhenSpiFailsAndThenSucceeds() {
        when(spiClient.processTransfer(10L))
                .thenReturn(false, false, true);

        boolean result = spiRetryService.processTransfer(10L);

        assertTrue(result);

        verify(spiClient, times(3))
                .processTransfer(10L);
    }

    @Test
    void processTransfer_shouldReturnFalseWhenAllAttemptsFail() {
        when(spiClient.processTransfer(10L))
                .thenReturn(false);

        boolean result = spiRetryService.processTransfer(10L);

        assertFalse(result);

        verify(spiClient, times(3))
                .processTransfer(10L);
    }

}