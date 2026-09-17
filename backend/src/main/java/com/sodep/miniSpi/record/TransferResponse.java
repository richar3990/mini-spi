package com.sodep.miniSpi.record;

import com.sodep.miniSpi.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long id,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount,
        String description,
        TransferStatus status,
        String idempotencyKey,
        LocalDateTime createdAt
) {
}
