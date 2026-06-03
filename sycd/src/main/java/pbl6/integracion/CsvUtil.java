package pbl6.integracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvUtil {
    private CsvUtil() {}

    public static Path dataDir() {
        String configured = System.getenv("ECOMOVE_DATA_DIR");
        if (configured == null || configured.isBlank()) configured = System.getenv("DATA_DIR");
        if (configured == null || configured.isBlank()) configured = "data";
        return Path.of(configured);
    }

    public static synchronized List<Map<String, String>> readRows(Path dataDir, String fileName) throws IOException {
        Path path = dataDir.resolve(fileName);
        if (!Files.exists(path)) return List.of();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();
        List<String> headers = parseLine(lines.get(0).replace("\uFEFF", ""));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            List<String> values = parseLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int h = 0; h < headers.size(); h++) {
                row.put(headers.get(h), h < values.size() ? values.get(h) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    public static synchronized void writeRows(Path dataDir, String fileName, List<String> headers, List<Map<String, String>> rows) throws IOException {
        Files.createDirectories(dataDir);
        List<String> lines = new ArrayList<>();
        lines.add(toCsvLine(headers));
        for (Map<String, String> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) values.add(row.getOrDefault(header, ""));
            lines.add(toCsvLine(values));
        }
        Files.write(dataDir.resolve(fileName), lines, StandardCharsets.UTF_8);
    }

    public static synchronized List<String> readHeaders(Path dataDir, String fileName) throws IOException {
        Path path = dataDir.resolve(fileName);
        if (!Files.exists(path)) return List.of();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();
        return parseLine(lines.get(0).replace("\uFEFF", ""));
    }

    public static List<String> parseLine(String line) {
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

    public static String toCsvLine(List<String> values) {
        List<String> escaped = new ArrayList<>();
        for (String value : values) escaped.add(escapeCsv(value));
        return String.join(",", escaped);
    }

    public static String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        boolean quote = safe.contains(",") || safe.contains("\n") || safe.contains("\r") || safe.contains("\"");
        safe = safe.replace("\"", "\"\"");
        return quote ? "\"" + safe + "\"" : safe;
    }

    public static long parseLong(String value) {
        try { return Long.parseLong(value == null || value.isBlank() ? "0" : value.trim()); }
        catch (Exception e) { return 0L; }
    }

    public static double parseDouble(String value) {
        try { return Double.parseDouble(value == null || value.isBlank() ? "0" : value.trim().replace(',', '.')); }
        catch (Exception e) { return 0.0; }
    }
}
