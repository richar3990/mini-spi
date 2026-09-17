package com.sodep.miniSpi.service;

import com.sodep.miniSpi.exception.DatabaseExceptionUtil;
import com.sodep.miniSpi.exception.TransferDatabaseException;
import com.sodep.miniSpi.query.TransferQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferCompensationService {
    private final JdbcTemplate jdbcTemplate;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateTransfer(Long transferId) {
        try {
            Boolean compensated = jdbcTemplate.queryForObject(
                    TransferQueries.COMPENSATE_TRANSFER,
                    Boolean.class,
                    transferId
            );

            if (!Boolean.TRUE.equals(compensated)) {
                throw new TransferDatabaseException("No se pudo compensar la transferencia.");
            }
        } catch (DataAccessException exception) {
            throw new TransferDatabaseException(DatabaseExceptionUtil.extractDatabaseMessage(exception));
        }
    }
}
