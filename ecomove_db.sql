CREATE DATABASE IF NOT EXISTS ecomove_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE ecomove_db;

CREATE TABLE IF NOT EXISTS usuarios_ecomove (
    userid BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresaid BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellidos VARCHAR(150) NOT NULL,
    nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
    contrasena VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    tiene_coche TINYINT(1) DEFAULT 0,
    modelo_cocheid VARCHAR(50) DEFAULT 'SIN_COCHE',
    pueblo_ciudad VARCHAR(100) NOT NULL
);

-- Estas tablas también las crea/actualiza automáticamente CsvDataService al arrancar la web.
-- El script queda como referencia por si quieres revisar la estructura en MariaDB/Grafana.
CREATE TABLE IF NOT EXISTS viajes_ecomove (
    `tripID` TEXT, `userID` TEXT, `fecha` TEXT, `origen` TEXT, `destino` TEXT, `km` TEXT, `co2` TEXT,
    `co2ConsumidoKg` TEXT, `co2AhorradoKg` TEXT, `modo` TEXT, `duracionMin` TEXT, `puntos` TEXT,
    `icono` TEXT, `tripTypeIcon` TEXT, `sessionID` TEXT, `startTimestamp` TEXT, `endTimestamp` TEXT,
    `origenLat` TEXT, `origenLon` TEXT, `destinoLat` TEXT, `destinoLon` TEXT, `duracionSeg` TEXT,
    `durationText` TEXT, `estadoCalculo` TEXT, `carpoolID` TEXT, `esCarpool` TEXT, `numPasajeros` TEXT,
    `rolCarpool` TEXT, `carpoolDriverSessionID` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ubicaciones_bidaia_ecomove (
    `trackingID` TEXT, `sessionID` TEXT, `userID` TEXT, `timestamp` TEXT, `latitud` TEXT, `longitud` TEXT,
    `accuracy` TEXT, `speed` TEXT, `heading` TEXT, `altitude` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS carpool_ofertas_ecomove (
    `offerID` TEXT, `userID` TEXT, `origen` TEXT, `destino` TEXT, `time` TEXT, `seats` TEXT,
    `active` TEXT, `distance` TEXT, `rating` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS carpool_uniones_ecomove (
    `joinID` TEXT, `offerID` TEXT, `userID` TEXT, `riderName` TEXT, `rol` TEXT, `fecha` TEXT, `estado` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS canjeos_ecomove (`redencionID` TEXT, `userID` TEXT, `rewardID` TEXT, `fecha` TEXT, `puntos` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS empresas_ecomove (`empresaID` TEXT, `nombre` TEXT, `ciudad` TEXT, `descripcion` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS coches_ecomove (`modeloCocheID` TEXT, `marca` TEXT, `modelo` TEXT, `tipo` TEXT, `emisionesKgKm` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS recompensas_ecomove (`rewardID` TEXT, `title` TEXT, `points` TEXT, `emoji` TEXT, `category` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS rutas_recomendadas_ecomove (`routeID` TEXT, `userID` TEXT, `origen` TEXT, `destino` TEXT, `duracion` TEXT, `distance` TEXT, `co2` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS ruta_pasos_ecomove (`routeID` TEXT, `orden` TEXT, `icon` TEXT, `label` TEXT, `detail` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS lineas_transporte_ecomove (`id` TEXT, `name` TEXT, `color` TEXT, `minutes` TEXT, `status` TEXT, `stops` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS paradas_transporte_ecomove (`paradaID` TEXT, `proveedor` TEXT, `stopID` TEXT, `stopCode` TEXT, `nombre` TEXT, `descripcion` TEXT, `latitud` TEXT, `longitud` TEXT, `zona` TEXT, `municipio` TEXT, `locationType` TEXT, `accesible` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS municipios_ecomove (`municipio` TEXT, `provincia` TEXT, `latitud` TEXT, `longitud` TEXT)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
