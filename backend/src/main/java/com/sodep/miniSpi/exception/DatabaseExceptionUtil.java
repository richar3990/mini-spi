package com.sodep.miniSpi.exception;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

public class DatabaseExceptionUtil {
    private DatabaseExceptionUtil() {}
    public static String extractDatabaseMessage(DataAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getMessage();
            }
            cause = cause.getCause();
        }
        return "No se pudo procesar la operación en la base de datos.";
    }
}
