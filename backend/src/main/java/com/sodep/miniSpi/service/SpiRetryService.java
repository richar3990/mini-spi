package com.sodep.miniSpi.service;

import com.sodep.miniSpi.spi.SpiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpiRetryService {
    private final SpiClient spiClient;

    @Value("${spi.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${spi.retry.initial-delay-ms:200}")
    private long initialDelayMs;

    public boolean processTransfer(Long transferId) {
        long delay = initialDelayMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean successful = spiClient.processTransfer(transferId);
            if (successful) {
                return true;
            }
            if (attempt < maxAttempts) {
                sleep(delay);
                delay *= 2;
            }
        }
        return false;
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("El retry del SPI fue interrumpido.", exception);
        }
    }
}
