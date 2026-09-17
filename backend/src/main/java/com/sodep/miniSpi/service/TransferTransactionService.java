package com.sodep.miniSpi.service;

import com.sodep.miniSpi.exception.DatabaseExceptionUtil;
import com.sodep.miniSpi.exception.TransferDatabaseException;
import com.sodep.miniSpi.query.TransferQueries;
import com.sodep.miniSpi.record.TransferProcessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransferTransactionService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public TransferProcessResult processTransfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String description,
            String idempotencyKey
    ) {
        try {
            return jdbcTemplate.queryForObject(
                    TransferQueries.PROCESS_TRANSFER,
                    (resultSet, rowNum) -> {
                        Object createdValue = resultSet.getObject("transferencia_creada");

                        System.out.println("transferencia_id = "
                                + resultSet.getObject("transferencia_id"));

                        System.out.println("transferencia_creada = ["
                                + createdValue + "]");

                        System.out.println("tipo transferencia_creada = "
                                + (createdValue == null
                                ? "null"
                                : createdValue.getClass().getName()));

                        return new TransferProcessResult(
                                resultSet.getLong("transferencia_id"),
                                resultSet.getBoolean("transferencia_creada")
                        );
                    },
                    sourceAccountId,
                    destinationAccountId,
                    amount,
                    description,
                    idempotencyKey
            );
        } catch (DataAccessException exception) {
            throw new TransferDatabaseException(
                    DatabaseExceptionUtil.extractDatabaseMessage(exception)
            );
        }
    }

    @Transactional
    public void markTransferSuccessful(Long transferId) {
        try {
            Boolean updated = jdbcTemplate.queryForObject(
                    TransferQueries.MARK_TRANSFER_SUCCESSFUL,
                    Boolean.class,
                    transferId
            );
            if (!Boolean.TRUE.equals(updated)) {
                throw new TransferDatabaseException("No se pudo marcar la transferencia como EXITOSA.");
            }
        } catch (DataAccessException exception) {
            throw new TransferDatabaseException(
                    DatabaseExceptionUtil.extractDatabaseMessage(exception)
            );
        }
    }
}
