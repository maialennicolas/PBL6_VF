package com.ecomove.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de datos del proyecto.
 *
 * Mantiene el nombre antiguo CsvDataService para no tener que reescribir toda la web,
 * pero las tablas principales ya se guardan en MariaDB. Los CSV de data/ se usan solo
 * como semilla inicial / exportación de apoyo.
 */
@Service
public class CsvDataService {

    private static final Map<String, DbTable> DB_TABLES = buildDbTables();

    private final Path dataDir;
    private final JdbcTemplate jdbc;

    public CsvDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String configuredDir = System.getenv("ECOMOVE_DATA_DIR");
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = System.getenv("DATA_DIR");
        }
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = "data";
        }
        this.dataDir = Path.of(configuredDir);
    }

    @PostConstruct
    public void initDatabaseTables() {
        ensureDataDir();
        for (DbTable table : DB_TABLES.values()) {
            try {
                createTableIfNeeded(table);
                importCsvSeedIfTableIsEmpty(table);
            } catch (Exception e) {
                System.err.println("[DbDataService] No se ha podido inicializar " + table.tableName + ": " + e.getMessage());
            }
        }
    }

    public synchronized List<Map<String, String>> readRows(String filename) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            try {
                return readRowsFromDb(table);
            } catch (Exception e) {
                System.err.println("[DbDataService] Error leyendo " + table.tableName + ", uso CSV de respaldo: " + e.getMessage());
            }
        }
        return readRowsFromFile(filename);
    }

    public synchronized String readRaw(String filename) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            List<Map<String, String>> rows = readRows(filename);
            List<String> lines = new ArrayList<>();
            lines.add(toCsvLine(table.headers));
            for (Map<String, String> row : rows) {
                lines.add(toCsvLine(table.headers.stream().map(header -> row.getOrDefault(header, "")).toList()));
            }
            return String.join(System.lineSeparator(), lines) + System.lineSeparator();
        }

        ensureDataDir();
        Path path = dataDir.resolve(filename);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido leer el CSV: " + filename, e);
        }
    }

    public synchronized void appendRow(String filename, List<String> headers, List<String> values) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), i < values.size() ? values.get(i) : "");
            }
            insertRow(table, row);
            return;
        }

        appendRowToFile(filename, headers, values);
    }

    public synchronized List<String> readHeaders(String filename) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            return table.headers;
        }
        return readHeadersFromFile(filename);
    }

    public synchronized long nextId(String filename, String idColumn) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            try {
                Number value = jdbc.queryForObject(
                        "SELECT COALESCE(MAX(CAST(" + q(idColumn) + " AS UNSIGNED)), 0) + 1 FROM " + q(table.tableName),
                        Number.class);
                return value == null ? 1L : value.longValue();
            } catch (Exception e) {
                return readRows(filename).stream()
                        .map(row -> parseLong(row.get(idColumn), 0))
                        .max(Long::compareTo)
                        .orElse(0L) + 1L;
            }
        }

        return readRows(filename).stream()
                .map(row -> parseLong(row.get(idColumn), 0))
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    public synchronized void writeRows(String filename, List<String> headers, List<Map<String, String>> rows) {
        DbTable table = DB_TABLES.get(filename);
        if (table != null) {
            jdbc.update("DELETE FROM " + q(table.tableName));
            for (Map<String, String> row : rows) {
                insertRow(table, row);
            }
            return;
        }

        writeRowsToFile(filename, headers, rows);
    }

    public Path getDataDir() {
        ensureDataDir();
        return dataDir.toAbsolutePath().normalize();
    }

    private static Map<String, DbTable> buildDbTables() {
        Map<String, DbTable> tables = new LinkedHashMap<>();

        tables.put("viajes.csv", new DbTable("viajes.csv", "viajes_ecomove", list(
                "tripID", "userID", "fecha", "origen", "destino", "km", "co2", "co2ConsumidoKg",
                "co2AhorradoKg", "modo", "duracionMin", "puntos", "icono", "tripTypeIcon", "sessionID",
                "startTimestamp", "endTimestamp", "origenLat", "origenLon", "destinoLat", "destinoLon",
                "duracionSeg", "durationText", "estadoCalculo", "carpoolID", "esCarpool", "numPasajeros",
                "rolCarpool", "carpoolDriverSessionID"), "tripID"));

        tables.put("ubicaciones_bidaia.csv", new DbTable("ubicaciones_bidaia.csv", "ubicaciones_bidaia_ecomove", list(
                "trackingID", "sessionID", "userID", "timestamp", "latitud", "longitud", "accuracy", "speed",
                "heading", "altitude"), "trackingID"));

        tables.put("carpool_ofertas.csv", new DbTable("carpool_ofertas.csv", "carpool_ofertas_ecomove", list(
                "offerID", "userID", "origen", "destino", "time", "seats", "active", "distance", "rating"), "offerID"));

        tables.put("carpool_uniones.csv", new DbTable("carpool_uniones.csv", "carpool_uniones_ecomove", list(
                "joinID", "offerID", "userID", "riderName", "rol", "fecha", "estado"), "joinID"));

        tables.put("canjeos.csv", new DbTable("canjeos.csv", "canjeos_ecomove", list(
                "redencionID", "userID", "rewardID", "fecha", "puntos"), "redencionID"));

        tables.put("empresas.csv", new DbTable("empresas.csv", "empresas_ecomove", list(
                "empresaID", "nombre", "ciudad", "descripcion"), "empresaID"));

        tables.put("coches.csv", new DbTable("coches.csv", "coches_ecomove", list(
                "modeloCocheID", "marca", "modelo", "tipo", "emisionesKgKm"), null));

        tables.put("recompensas.csv", new DbTable("recompensas.csv", "recompensas_ecomove", list(
                "rewardID", "title", "points", "emoji", "category"), "rewardID"));

        tables.put("rutas_recomendadas.csv", new DbTable("rutas_recomendadas.csv", "rutas_recomendadas_ecomove", list(
                "routeID", "userID", "origen", "destino", "duracion", "distance", "co2"), "routeID"));

        tables.put("ruta_pasos.csv", new DbTable("ruta_pasos.csv", "ruta_pasos_ecomove", list(
                "routeID", "orden", "icon", "label", "detail"), null));

        tables.put("lineas_transporte.csv", new DbTable("lineas_transporte.csv", "lineas_transporte_ecomove", list(
                "id", "name", "color", "minutes", "status", "stops"), null));

        tables.put("paradas_transporte.csv", new DbTable("paradas_transporte.csv", "paradas_transporte_ecomove", list(
                "paradaID", "proveedor", "stopID", "stopCode", "nombre", "descripcion", "latitud", "longitud",
                "zona", "municipio", "locationType", "accesible"), "paradaID"));

        tables.put("municipios.csv", new DbTable("municipios.csv", "municipios_ecomove", list(
                "municipio", "provincia", "latitud", "longitud"), null));

        return tables;
    }

    private static List<String> list(String... values) {
        return List.copyOf(Arrays.asList(values));
    }

    private void createTableIfNeeded(DbTable table) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(q(table.tableName)).append(" (");
        for (int i = 0; i < table.headers.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(q(table.headers.get(i))).append(" TEXT");
        }
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        jdbc.execute(sql.toString());

        for (String header : table.headers) {
            ensureColumnExists(table.tableName, header);
        }
    }

    private void ensureColumnExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName);
        if (count == null || count == 0) {
            jdbc.execute("ALTER TABLE " + q(tableName) + " ADD COLUMN " + q(columnName) + " TEXT");
        }
    }

    private void importCsvSeedIfTableIsEmpty(DbTable table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(table.tableName), Integer.class);
        if (count != null && count > 0) {
            return;
        }

        List<Map<String, String>> seedRows = readRowsFromFile(table.filename);
        if (seedRows.isEmpty()) {
            return;
        }

        for (Map<String, String> row : seedRows) {
            insertRow(table, row);
        }
        System.out.println("[DbDataService] " + table.tableName + " inicializada desde " + table.filename + " (" + seedRows.size() + " filas)");
    }

    private List<Map<String, String>> readRowsFromDb(DbTable table) {
        String sql = "SELECT " + table.headers.stream().map(CsvDataService::q).reduce((a, b) -> a + ", " + b).orElse("*")
                + " FROM " + q(table.tableName);
        if (table.idColumn != null && !table.idColumn.isBlank()) {
            sql += " ORDER BY CAST(" + q(table.idColumn) + " AS UNSIGNED)";
        }

        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, String> row = new LinkedHashMap<>();
            for (String header : table.headers) {
                String value = rs.getString(header);
                row.put(header, value == null ? "" : value);
            }
            return row;
        });
    }

    private void insertRow(DbTable table, Map<String, String> row) {
        String columns = table.headers.stream().map(CsvDataService::q).reduce((a, b) -> a + ", " + b).orElse("");
        String placeholders = table.headers.stream().map(h -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        Object[] values = table.headers.stream().map(header -> row.getOrDefault(header, "")).toArray();
        jdbc.update("INSERT INTO " + q(table.tableName) + " (" + columns + ") VALUES (" + placeholders + ")", values);
    }

    private void mirrorDbTableToCsv(DbTable table) {
        // Mantiene una copia legible en data/ para exportaciones y depuración.
        // La fuente real de datos es MariaDB.
        try {
            List<Map<String, String>> rows = readRowsFromDb(table);
            writeRowsToFile(table.filename, table.headers, rows);
        } catch (Exception ignored) {
            // No rompemos la app si el espejo CSV falla.
        }
    }

    private List<Map<String, String>> readRowsFromFile(String filename) {
        ensureDataDir();
        Path path = dataDir.resolve(filename);

        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }

            List<String> headers = parseLine(lines.get(0).replace("\uFEFF", ""));
            List<Map<String, String>> rows = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }

                List<String> values = parseLine(line);
                Map<String, String> row = new LinkedHashMap<>();

                for (int h = 0; h < headers.size(); h++) {
                    row.put(headers.get(h), h < values.size() ? values.get(h) : "");
                }

                rows.add(row);
            }

            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido leer el CSV: " + filename, e);
        }
    }

    private void appendRowToFile(String filename, List<String> headers, List<String> values) {
        ensureDataDir();
        Path path = dataDir.resolve(filename);

        try {
            if (!Files.exists(path)) {
                Files.writeString(path, toCsvLine(headers) + System.lineSeparator(), StandardCharsets.UTF_8);
            }

<<<<<<< HEAD
            List<String> currentHeaders = readHeadersFromFile(filename);
=======
            List<String> currentHeaders = readHeaders(filename);
>>>>>>> cc5ed4dfae45db41af6469d1c5fade7ad98a6143
            List<String> mergedHeaders = new ArrayList<>(currentHeaders);
            boolean changed = false;

            for (String header : headers) {
                if (!mergedHeaders.contains(header)) {
                    mergedHeaders.add(header);
                    changed = true;
                }
            }

            if (changed) {
<<<<<<< HEAD
                List<Map<String, String>> rows = readRowsFromFile(filename);
                writeRowsToFile(filename, mergedHeaders, rows);
=======
                List<Map<String, String>> rows = readRows(filename);
                writeRows(filename, mergedHeaders, rows);
>>>>>>> cc5ed4dfae45db41af6469d1c5fade7ad98a6143
                currentHeaders = mergedHeaders;
            }

            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), i < values.size() ? values.get(i) : "");
            }

            List<String> lineValues = currentHeaders.stream()
                    .map(header -> row.getOrDefault(header, ""))
                    .toList();

            String line = toCsvLine(lineValues) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido guardar en el CSV: " + filename, e);
        }
    }

<<<<<<< HEAD
    private List<String> readHeadersFromFile(String filename) {
=======
    public synchronized List<String> readHeaders(String filename) {
>>>>>>> cc5ed4dfae45db41af6469d1c5fade7ad98a6143
        ensureDataDir();
        Path path = dataDir.resolve(filename);

        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }
            return parseLine(lines.get(0).replace("\uFEFF", ""));
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido leer la cabecera del CSV: " + filename, e);
        }
<<<<<<< HEAD
=======
    }

    public synchronized long nextId(String filename, String idColumn) {
        return readRows(filename).stream()
                .map(row -> parseLong(row.get(idColumn), 0))
                .max(Long::compareTo)
                .orElse(0L) + 1L;
>>>>>>> cc5ed4dfae45db41af6469d1c5fade7ad98a6143
    }

    private void writeRowsToFile(String filename, List<String> headers, List<Map<String, String>> rows) {
        ensureDataDir();
        Path path = dataDir.resolve(filename);

        try {
            List<String> lines = new ArrayList<>();
            lines.add(toCsvLine(headers));

            for (Map<String, String> row : rows) {
                List<String> values = headers.stream()
                        .map(header -> row.getOrDefault(header, ""))
                        .toList();

                lines.add(toCsvLine(values));
            }

            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido actualizar el CSV: " + filename, e);
        }
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido crear la carpeta data", e);
        }
    }

    private List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());
        return values;
    }

    private String toCsvLine(List<String> values) {
        return values.stream().map(this::escapeCsv).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        boolean mustQuote = safe.contains(",") || safe.contains("\n") || safe.contains("\r") || safe.contains("\"");
        safe = safe.replace("\"", "\"\"");
        return mustQuote ? "\"" + safe + "\"" : safe;
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value == null || value.isBlank() ? String.valueOf(fallback) : value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String q(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private record DbTable(String filename, String tableName, List<String> headers, String idColumn) {}
}
