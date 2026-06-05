USE ecomove_db;

CREATE TABLE IF NOT EXISTS co2_auditoria_ecomove (
    auditID BIGINT AUTO_INCREMENT PRIMARY KEY,
    userID BIGINT NOT NULL,
    empresaID BIGINT NOT NULL,
    sessionID VARCHAR(120) NOT NULL,
    sycdMode VARCHAR(60),
    webMode VARCHAR(60),
    km DECIMAL(14,4) DEFAULT 0,
    co2ConsumidoKg DECIMAL(14,4) DEFAULT 0,
    co2AhorradoKg DECIMAL(14,4) DEFAULT 0,
    puntos INT DEFAULT 0,
    esCarpool TINYINT(1) DEFAULT 0,
    numPasajeros INT DEFAULT 1,
    latitud VARCHAR(40),
    longitud VARCHAR(40),
    eventTimestamp BIGINT,
    fechaEvento DATETIME,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_co2_audit_user_session (userID, sessionID),
    INDEX idx_co2_audit_empresa (empresaID),
    INDEX idx_co2_audit_fecha (fechaEvento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'co2_auditoria_ecomove lista' AS resultado;
