package com.sodep.miniSpi.integration;

import com.sodep.miniSpi.exception.TransferDatabaseException;
import com.sodep.miniSpi.record.TransferProcessResult;
import com.sodep.miniSpi.service.TransferCompensationService;
import com.sodep.miniSpi.service.TransferTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class TransferIntegrationTest {

    @Autowired
    private TransferCompensationService transferCompensationService;

    @BeforeEach
    void resetTestData() {
        jdbcTemplate.execute("DELETE FROM transferencias");

        jdbcTemplate.update(
                "UPDATE cuentas SET saldo = ? WHERE id = ?",
                new BigDecimal("1000000.00"),
                1L
        );

        jdbcTemplate.update(
                "UPDATE cuentas SET saldo = ? WHERE id = ?",
                new BigDecimal("500000.00"),
                2L
        );

        jdbcTemplate.update(
                "UPDATE cuentas SET saldo = ? WHERE id = ?",
                new BigDecimal("250000.00"),
                3L
        );

        jdbcTemplate.update(
                "UPDATE cuentas SET saldo = ? WHERE id = ?",
                new BigDecimal("100000.00"),
                4L
        );
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withInitScript("db/init.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferTransactionService transferTransactionService;

    @Test
    void postgresContainer_shouldBeAvailable() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        assertThat(result).isEqualTo(1);
    }

    @Test
    void processTransfer_shouldDebitSourceAndCreditDestination() {
        TransferProcessResult result =
                transferTransactionService.processTransfer(
                        1L,
                        2L,
                        new BigDecimal("1000.00"),
                        "Transferencia de prueba",
                        UUID.randomUUID().toString()
                );

        assertThat(result).isNotNull();
        assertThat(result.created()).isTrue();

        BigDecimal sourceBalance = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalance = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        assertThat(sourceBalance)
                .isEqualByComparingTo("999000.00");

        assertThat(destinationBalance)
                .isEqualByComparingTo("501000.00");
    }

    @Test
    void processTransfer_shouldRollbackWhenInsufficientFunds() {
        BigDecimal sourceBalanceBefore = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalanceBefore = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        String idempotencyKey = UUID.randomUUID().toString();

        assertThatThrownBy(() ->
                transferTransactionService.processTransfer(
                        1L,
                        2L,
                        new BigDecimal("2000000.00"),
                        "Transferencia sin fondos",
                        idempotencyKey
                )
        )
                .isInstanceOf(TransferDatabaseException.class)
                .hasMessageContaining("Fondos insuficientes");

        BigDecimal sourceBalanceAfter = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalanceAfter = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        Integer transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transferencias WHERE idempotency_key = ?",
                Integer.class,
                idempotencyKey
        );

        assertThat(sourceBalanceAfter)
                .isEqualByComparingTo(sourceBalanceBefore);

        assertThat(destinationBalanceAfter)
                .isEqualByComparingTo(destinationBalanceBefore);

        assertThat(transferCount)
                .isZero();
    }

    @Test
    void processTransfer_shouldReturnSameTransferForSameIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();

        TransferProcessResult firstResult =
                transferTransactionService.processTransfer(
                        1L,
                        2L,
                        new BigDecimal("1000.00"),
                        "Transferencia idempotente",
                        idempotencyKey
                );

        TransferProcessResult secondResult =
                transferTransactionService.processTransfer(
                        1L,
                        2L,
                        new BigDecimal("1000.00"),
                        "Transferencia idempotente",
                        idempotencyKey
                );

        assertThat(firstResult.created())
                .isTrue();

        assertThat(secondResult.created())
                .isFalse();

        assertThat(secondResult.transferId())
                .isEqualTo(firstResult.transferId());

        Integer transferCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transferencias
                WHERE idempotency_key = ?
                """,
                Integer.class,
                idempotencyKey
        );

        assertThat(transferCount)
                .isEqualTo(1);

        BigDecimal sourceBalance = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalance = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        assertThat(sourceBalance)
                .isEqualByComparingTo("999000.00");

        assertThat(destinationBalance)
                .isEqualByComparingTo("501000.00");
    }

    @Test
    void compensateTransfer_shouldReverseTransferAndMarkAsRejected() {
        String idempotencyKey = UUID.randomUUID().toString();

        TransferProcessResult processResult =
                transferTransactionService.processTransfer(
                        1L,
                        2L,
                        new BigDecimal("1000.00"),
                        "Transferencia a compensar",
                        idempotencyKey
                );

        assertThat(processResult.created())
                .isTrue();

        Long transferId = processResult.transferId();

        BigDecimal sourceBalanceAfterTransfer = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalanceAfterTransfer = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        assertThat(sourceBalanceAfterTransfer)
                .isEqualByComparingTo("999000.00");

        assertThat(destinationBalanceAfterTransfer)
                .isEqualByComparingTo("501000.00");

        transferCompensationService.compensateTransfer(transferId);

        BigDecimal sourceBalanceAfterCompensation = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 1",
                BigDecimal.class
        );

        BigDecimal destinationBalanceAfterCompensation = jdbcTemplate.queryForObject(
                "SELECT saldo FROM cuentas WHERE id = 2",
                BigDecimal.class
        );

        String status = jdbcTemplate.queryForObject(
                "SELECT estado::text FROM transferencias WHERE id = ?",
                String.class,
                transferId
        );

        assertThat(sourceBalanceAfterCompensation)
                .isEqualByComparingTo("1000000.00");

        assertThat(destinationBalanceAfterCompensation)
                .isEqualByComparingTo("500000.00");

        assertThat(status)
                .isEqualTo("RECHAZADA");
    }
}
