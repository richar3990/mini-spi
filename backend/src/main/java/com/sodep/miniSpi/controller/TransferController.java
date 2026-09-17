package com.sodep.miniSpi.controller;

import com.sodep.miniSpi.record.CreateTransferRequest;
import com.sodep.miniSpi.record.TransferResponse;
import com.sodep.miniSpi.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transferencias")
@RequiredArgsConstructor
public class TransferController {
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey
    ) {
        TransferResponse response = transferService.createTransfer(request, idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getLatestTransfers() {
        return ResponseEntity.ok(transferService.getLatestTransfers());
    }
}
