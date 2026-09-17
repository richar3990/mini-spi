-- ============================================================
-- MINI SPI - DATABASE INITIALIZATION
-- PostgreSQL
-- ============================================================

-- ============================================================
-- 1. LIMPIEZA
-- ============================================================

DROP TABLE IF EXISTS transferencias CASCADE;
DROP TABLE IF EXISTS cuentas CASCADE;

DROP TYPE IF EXISTS estado_transferencia CASCADE;


-- ============================================================
-- 2. TIPOS
-- ============================================================

CREATE TYPE estado_transferencia AS ENUM (
    'PENDIENTE',
    'EXITOSA',
    'RECHAZADA'
);


-- ============================================================
-- 3. TABLA CUENTAS
-- ============================================================

CREATE TABLE cuentas (
                         id BIGSERIAL PRIMARY KEY,

                         numero_cuenta VARCHAR(30) NOT NULL,

                         saldo NUMERIC(19, 2) NOT NULL DEFAULT 0,

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT uk_cuentas_numero_cuenta
                             UNIQUE (numero_cuenta),

                         CONSTRAINT chk_cuentas_saldo_no_negativo
                             CHECK (saldo >= 0)
);


-- ============================================================
-- 4. TABLA TRANSFERENCIAS
-- ============================================================

CREATE TABLE transferencias (
                                id BIGSERIAL PRIMARY KEY,

                                cuenta_origen BIGINT NOT NULL,

                                cuenta_destino BIGINT NOT NULL,

                                monto NUMERIC(19, 2) NOT NULL,

                                concepto VARCHAR(255) NOT NULL,

                                estado estado_transferencia NOT NULL,

                                idempotency_key VARCHAR(100) NOT NULL,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_transferencias_cuenta_origen
                                    FOREIGN KEY (cuenta_origen)
                                        REFERENCES cuentas(id),

                                CONSTRAINT fk_transferencias_cuenta_destino
                                    FOREIGN KEY (cuenta_destino)
                                        REFERENCES cuentas(id),

                                CONSTRAINT uk_transferencias_idempotency_key
                                    UNIQUE (idempotency_key),

                                CONSTRAINT chk_transferencias_monto_positivo
                                    CHECK (monto > 0),

                                CONSTRAINT chk_transferencias_cuentas_diferentes
                                    CHECK (cuenta_origen <> cuenta_destino),

                                CONSTRAINT chk_transferencias_concepto_no_vacio
                                    CHECK (length(trim(concepto)) > 0)
);


-- ============================================================
-- 5. ÍNDICES
-- ============================================================

CREATE INDEX idx_transferencias_cuenta_origen
    ON transferencias(cuenta_origen);

CREATE INDEX idx_transferencias_cuenta_destino
    ON transferencias(cuenta_destino);

CREATE INDEX idx_transferencias_created_at
    ON transferencias(created_at DESC);

CREATE INDEX idx_transferencias_estado
    ON transferencias(estado);

CREATE INDEX idx_transferencias_estado_created_at
    ON transferencias(estado, created_at DESC);


-- ============================================================
-- 6. DATOS INICIALES
-- ============================================================

INSERT INTO cuentas (
    numero_cuenta,
    saldo
) VALUES
      ('10000001', 1000000.00),
      ('10000002', 500000.00),
      ('10000003', 250000.00),
      ('10000004', 100000.00);


DROP FUNCTION IF EXISTS procesar_transferencia(
    BIGINT,
    BIGINT,
    NUMERIC,
    VARCHAR,
    VARCHAR
    );

CREATE FUNCTION procesar_transferencia(
    p_cuenta_origen BIGINT,
    p_cuenta_destino BIGINT,
    p_monto NUMERIC(19,2),
    p_concepto VARCHAR(255),
    p_idempotency_key VARCHAR(100)
)
    RETURNS TABLE (
                      transferencia_id BIGINT,
                      transferencia_creada BOOLEAN
                  )
    LANGUAGE plpgsql
AS $$
DECLARE
v_saldo_origen NUMERIC(19,2);
BEGIN

    IF p_cuenta_origen IS NULL
       OR p_cuenta_destino IS NULL
       OR p_monto IS NULL
       OR p_monto <= 0
       OR p_idempotency_key IS NULL
       OR trim(p_idempotency_key) = '' THEN

        RAISE EXCEPTION 'Datos de transferencia invalidos';
END IF;

    IF p_cuenta_origen = p_cuenta_destino THEN
        RAISE EXCEPTION 'La cuenta origen y destino deben ser diferentes';
END IF;

INSERT INTO transferencias (
    cuenta_origen,
    cuenta_destino,
    monto,
    concepto,
    estado,
    idempotency_key
)
VALUES (
           p_cuenta_origen,
           p_cuenta_destino,
           p_monto,
           p_concepto,
           'PENDIENTE',
           p_idempotency_key
       )
    ON CONFLICT (idempotency_key) DO NOTHING
    RETURNING id INTO transferencia_id;

IF NOT FOUND THEN

SELECT id
INTO transferencia_id
FROM transferencias
WHERE idempotency_key = p_idempotency_key;

transferencia_creada := FALSE;

RETURN QUERY
SELECT transferencia_id, transferencia_creada;

RETURN;
END IF;

    transferencia_creada := TRUE;

    IF p_cuenta_origen < p_cuenta_destino THEN

        PERFORM 1
        FROM cuentas
        WHERE id = p_cuenta_origen
        FOR UPDATE;

PERFORM 1
        FROM cuentas
        WHERE id = p_cuenta_destino
        FOR UPDATE;

ELSE

        PERFORM 1
        FROM cuentas
        WHERE id = p_cuenta_destino
        FOR UPDATE;

PERFORM 1
        FROM cuentas
        WHERE id = p_cuenta_origen
        FOR UPDATE;

END IF;

SELECT saldo
INTO v_saldo_origen
FROM cuentas
WHERE id = p_cuenta_origen;

IF v_saldo_origen IS NULL THEN
        RAISE EXCEPTION 'La cuenta origen no existe';
END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM cuentas
        WHERE id = p_cuenta_destino
    ) THEN
        RAISE EXCEPTION 'La cuenta destino no existe';
END IF;

    IF v_saldo_origen < p_monto THEN
        RAISE EXCEPTION 'Fondos insuficientes';
END IF;

UPDATE cuentas
SET saldo = saldo - p_monto,
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_cuenta_origen;

UPDATE cuentas
SET saldo = saldo + p_monto,
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_cuenta_destino;

RETURN QUERY
SELECT transferencia_id, transferencia_creada;

RETURN;
END;
$$;


DROP FUNCTION IF EXISTS marcar_transferencia_exitosa(BIGINT);

CREATE FUNCTION marcar_transferencia_exitosa(
    p_transferencia_id BIGINT
)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
AS $$
BEGIN

UPDATE transferencias
SET estado = 'EXITOSA',
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_transferencia_id
  AND estado = 'PENDIENTE';

RETURN FOUND;

END;
$$;


DROP FUNCTION IF EXISTS compensar_transferencia(BIGINT);

CREATE FUNCTION compensar_transferencia(
    p_transferencia_id BIGINT
)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
AS $$
DECLARE
v_cuenta_origen BIGINT;
    v_cuenta_destino BIGINT;
    v_monto NUMERIC(19,2);
    v_estado estado_transferencia;
BEGIN
SELECT
    cuenta_origen,
    cuenta_destino,
    monto,
    estado
INTO
    v_cuenta_origen,
    v_cuenta_destino,
    v_monto,
    v_estado
FROM transferencias
WHERE id = p_transferencia_id
    FOR UPDATE;

IF v_estado IS NULL THEN
        RAISE EXCEPTION 'La transferencia no existe';
END IF;

    IF v_estado <> 'PENDIENTE' THEN
        RETURN FALSE;
END IF;

UPDATE cuentas
SET saldo = saldo + v_monto,
    updated_at = CURRENT_TIMESTAMP
WHERE id = v_cuenta_origen;

UPDATE cuentas
SET saldo = saldo - v_monto,
    updated_at = CURRENT_TIMESTAMP
WHERE id = v_cuenta_destino;

UPDATE transferencias
SET estado = 'RECHAZADA',
    updated_at = CURRENT_TIMESTAMP
WHERE id = p_transferencia_id;

RETURN TRUE;
END;
$$;