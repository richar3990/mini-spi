package com.sodep.miniSpi.service;

import com.sodep.miniSpi.entity.Account;
import com.sodep.miniSpi.entity.Transfer;
import com.sodep.miniSpi.enums.TransferStatus;
import com.sodep.miniSpi.record.CreateTransferRequest;
import com.sodep.miniSpi.record.TransferProcessResult;
import com.sodep.miniSpi.record.TransferResponse;
import com.sodep.miniSpi.repository.AccountRepository;
import com.sodep.miniSpi.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private TransferTransactionService transferTransactionService;

    @Mock
    private TransferCompensationService transferCompensationService;

    @Mock
    private SpiRetryService spiRetryService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TransferService transferService;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = createAccount(
                1L,
                "10000001"
        );

        destinationAccount = createAccount(
                2L,
                "10000002"
        );
    }

    @Test
    void createTransfer_shouldReturnSuccessfulTransfer() {
        CreateTransferRequest request = new CreateTransferRequest(
                "10000001",
                "10000002",
                new BigDecimal("1000.00"),
                "Transferencia de prueba"
        );

        String idempotencyKey = "test-success-001";

        TransferProcessResult processResult =
                new TransferProcessResult(10L, true);

        Transfer transfer = createTransfer(
                10L,
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.description(),
                TransferStatus.PENDIENTE,
                idempotencyKey
        );

        Transfer successfulTransfer = createTransfer(
                10L,
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.description(),
                TransferStatus.EXITOSA,
                idempotencyKey
        );

        when(transferRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountNumber("10000001"))
                .thenReturn(Optional.of(sourceAccount));

        when(accountRepository.findByAccountNumber("10000002"))
                .thenReturn(Optional.of(destinationAccount));

        when(transferTransactionService.processTransfer(
                1L,
                2L,
                request.amount(),
                request.description(),
                idempotencyKey
        )).thenReturn(processResult);

        when(transferRepository.findById(10L))
                .thenReturn(Optional.of(transfer))
                .thenReturn(Optional.of(successfulTransfer));

        when(spiRetryService.processTransfer(10L))
                .thenReturn(true);

        TransferResponse response =
                transferService.createTransfer(
                        request,
                        idempotencyKey
                );

        assertEquals(10L, response.id());
        assertEquals("10000001", response.sourceAccount());
        assertEquals("10000002", response.destinationAccount());
        assertEquals(new BigDecimal("1000.00"), response.amount());
        assertEquals("Transferencia de prueba", response.description());
        assertEquals(TransferStatus.EXITOSA, response.status());
        assertEquals(idempotencyKey, response.idempotencyKey());

        verify(spiRetryService).processTransfer(10L);

        verify(transferTransactionService)
                .markTransferSuccessful(10L);

        verify(transferCompensationService, never())
                .compensateTransfer(anyLong());
        verify(entityManager).clear();
    }

    private Account createAccount(Long id, String accountNumber) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNumber(accountNumber);
        account.setBalance(new BigDecimal("100000.00"));
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }

    private Transfer createTransfer(
            Long id,
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount,
            String description,
            TransferStatus status,
            String idempotencyKey
    ) {
        Transfer transfer = new Transfer();
        transfer.setId(id);
        transfer.setSourceAccount(sourceAccount);
        transfer.setDestinationAccount(destinationAccount);
        transfer.setAmount(amount);
        transfer.setDescription(description);
        transfer.setStatus(status);
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setUpdatedAt(LocalDateTime.now());
        return transfer;
    }

    @Test
    void createTransfer_shouldCompensateWhenSpiFails() {
        CreateTransferRequest request = new CreateTransferRequest(
                "10000001",
                "10000002",
                new BigDecimal("1000.00"),
                "Prueba"
        );

        Transfer pendingTransfer = createTransfer(
                10L,
                sourceAccount,
                destinationAccount,
                new BigDecimal("1000.00"),
                "Prueba",
                TransferStatus.PENDIENTE,
                "key-123"
        );

        Transfer rejectedTransfer = createTransfer(
                10L,
                sourceAccount,
                destinationAccount,
                new BigDecimal("1000.00"),
                "Prueba",
                TransferStatus.RECHAZADA,
                "key-123"
        );

        when(transferRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.empty());

        when(accountRepository.findByAccountNumber("10000001"))
                .thenReturn(Optional.of(pendingTransfer.getSourceAccount()));

        when(accountRepository.findByAccountNumber("10000002"))
                .thenReturn(Optional.of(pendingTransfer.getDestinationAccount()));

        when(transferTransactionService.processTransfer(
                anyLong(),
                anyLong(),
                any(BigDecimal.class),
                anyString(),
                eq("key-123")
        )).thenReturn(new TransferProcessResult(10L, true));

        when(transferRepository.findById(10L))
                .thenReturn(
                        Optional.of(pendingTransfer),
                        Optional.of(rejectedTransfer)
                );

        when(spiRetryService.processTransfer(10L))
                .thenReturn(false);

        TransferResponse response =
                transferService.createTransfer(request, "key-123");

        assertEquals(TransferStatus.RECHAZADA, response.status());

        verify(spiRetryService).processTransfer(10L);
        verify(transferCompensationService).compensateTransfer(10L);
        verify(transferTransactionService, never())
                .markTransferSuccessful(anyLong());

        verify(entityManager).clear();
    }

    @Test
    void createTransfer_shouldReturnExistingTransferWhenIdempotencyKeyExists() {
        CreateTransferRequest request = new CreateTransferRequest(
                "10000001",
                "10000002",
                new BigDecimal("1000.00"),
                "Prueba"
        );

        Transfer existingTransfer = createTransfer(
                10L,
                sourceAccount,
                destinationAccount,
                new BigDecimal("1000.00"),
                "Prueba",
                TransferStatus.EXITOSA,
                "key-123"
        );

        when(transferRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.of(existingTransfer));

        TransferResponse response =
                transferService.createTransfer(request, "key-123");

        assertEquals(TransferStatus.EXITOSA, response.status());
        assertEquals(10L, response.id());

        verify(accountRepository, never())
                .findByAccountNumber(anyString());

        verify(transferTransactionService, never())
                .processTransfer(
                        anyLong(),
                        anyLong(),
                        any(BigDecimal.class),
                        anyString(),
                        anyString()
                );

        verify(spiRetryService, never())
                .processTransfer(anyLong());
    }
}