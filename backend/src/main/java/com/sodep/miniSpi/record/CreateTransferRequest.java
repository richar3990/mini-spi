package com.sodep.miniSpi.record;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTransferRequest(

        @NotBlank(message = "La cuenta origen es obligatoria.")
        @Size(max = 30, message = "La cuenta origen no puede superar los 30 caracteres.")
        String sourceAccount,

        @NotBlank(message = "La cuenta destino es obligatoria.")
        @Size(max = 30, message = "La cuenta destino no puede superar los 30 caracteres.")
        String destinationAccount,

        @NotNull(message = "El monto es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero.")
        BigDecimal amount,

        @NotBlank(message = "El concepto es obligatorio.")
        @Size(max = 255, message = "El concepto no puede superar los 255 caracteres.")
        String description
) {
}
