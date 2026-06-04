CREATE DATABASE IF NOT EXISTS matomo_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS ecomove_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE ecomove_db;

-- 3. Creamos la tabla de usuarios para EcoMove
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