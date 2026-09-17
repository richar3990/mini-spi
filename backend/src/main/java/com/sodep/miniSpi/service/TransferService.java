package com.sodep.miniSpi.service;

import com.sodep.miniSpi.entity.Account;
import com.sodep.miniSpi.entity.Transfer;
import com.sodep.miniSpi.record.CreateTransferRequest;
import com.sodep.miniSpi.record.TransferProcessResult;
import com.sodep.miniSpi.record.TransferResponse;
import com.sodep.miniSpi.repository.AccountRepository;
import com.sodep.miniSpi.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final TransferTransactionService transferTransactionService;
    private final TransferCompensationService transferCompensationService;
    private final SpiRetryService spiRetryService;
    private final EntityManager entityManager;

    public List<TransferResponse> getLatestTransfers() {
        return transferRepository
                .findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TransferResponse createTransfer(
            CreateTransferRequest request,
            String idempotencyKey
    ) {
        Transfer existingTransfer = transferRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existingTransfer != null) {
            return toResponse(existingTransfer);
        }

        Account sourceAccount = accountRepository
                .findByAccountNumber(request.sourceAccount())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La cuenta origen no existe."
                        )
                );

        Account destinationAccount = accountRepository
                .findByAccountNumber(request.destinationAccount())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "La cuenta destino no existe."
                        )
                );

        TransferProcessResult processResult =
                transferTransactionService.processTransfer(
                        sourceAccount.getId(),
                        destinationAccount.getId(),
                        request.amount(),
                        request.description(),
                        idempotencyKey
                );

        Transfer transfer = transferRepository
                .findById(processResult.transferId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No se pudo recuperar la transferencia procesada."
                        )
                );

        if (!processResult.created()) {
            return toResponse(transfer);
        }

        boolean spiSuccessful =  spiRetryService.processTransfer(transfer.getId());
        if (spiSuccessful) {
            transferTransactionService.markTransferSuccessful(transfer.getId());
        } else {
            transferCompensationService.compensateTransfer(transfer.getId());
        }
        entityManager.clear();
        Transfer updatedTransfer = transferRepository
                .findById(transfer.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No se pudo recuperar el estado final de la transferencia."
                        )
                );

        return toResponse(updatedTransfer);
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getAccountNumber(),
                transfer.getDestinationAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getDescription(),
                transfer.getStatus(),
                transfer.getIdempotencyKey(),
                transfer.getCreatedAt()
        );
    }
}
