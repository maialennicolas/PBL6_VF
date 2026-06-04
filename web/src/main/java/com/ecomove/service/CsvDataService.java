package com.ecomove.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CsvDataService {

    private final Path dataDir;

    public CsvDataService() {
        String configuredDir = System.getenv("ECOMOVE_DATA_DIR");
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = System.getenv("DATA_DIR");
        }
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = "data";
        }
        this.dataDir = Path.of(configuredDir);
    }

    public synchronized List<Map<String, String>> readRows(String filename) {
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

            List<String> headers = parseLine(lines.get(0));
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

    public synchronized String readRaw(String filename) {
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
        ensureDataDir();
        Path path = dataDir.resolve(filename);

        try {
            if (!Files.exists(path)) {
                Files.writeString(path, toCsvLine(headers) + System.lineSeparator(), StandardCharsets.UTF_8);
            }

            List<String> currentHeaders = readHeaders(filename);
            List<String> mergedHeaders = new ArrayList<>(currentHeaders);
            boolean changed = false;

            for (String header : headers) {
                if (!mergedHeaders.contains(header)) {
                    mergedHeaders.add(header);
                    changed = true;
                }
            }

            if (changed) {
                List<Map<String, String>> rows = readRows(filename);
                writeRows(filename, mergedHeaders, rows);
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

    public synchronized List<String> readHeaders(String filename) {
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
    }

    public synchronized long nextId(String filename, String idColumn) {
        return readRows(filename).stream()
                .map(row -> parseLong(row.get(idColumn), 0))
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }

    public synchronized void writeRows(String filename, List<String> headers, List<Map<String, String>> rows) {
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

    /*
     * public User updateProfile(ProfileUpdateRequest request) {
     * 
     * 
     * throw new IllegalArgumentException("Usuario no encontrado");
     * }
     */

    /*
     * private String safe(String value, String fallback) {
     * return value == null || value.isBlank() ? fallback : value.trim();
     * }
     */

    public Path getDataDir() {
        ensureDataDir();
        return dataDir.toAbsolutePath().normalize();
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
            return Long.parseLong(value == null || value.isBlank() ? String.valueOf(fallback) : value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
