package com.sodep.miniSpi.query;

public class TransferQueries {
    private TransferQueries() {}
    public static final String PROCESS_TRANSFER = """
            SELECT *
            FROM procesar_transferencia(?, ?, ?, ?, ?)
            """;

    public static final String MARK_TRANSFER_SUCCESSFUL = """
            SELECT marcar_transferencia_exitosa(?)
            """;

    public static final String COMPENSATE_TRANSFER = """
            SELECT compensar_transferencia(?)
            """;
}
