package com.ecomove.service;

import com.ecomove.model.CarModel;
import com.ecomove.model.Empresa;
import com.ecomove.model.User;
import org.springframework.stereotype.Service;

import com.ecomove.model.ProfileUpdateRequest;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserCsvService {

    public static final String USERS_FILE = "usuarios.csv";
    public static final String COMPANIES_FILE = "empresas.csv";
    public static final String CARS_FILE = "coches.csv";

    private static final List<String> USER_HEADERS = List.of(
            "userID", "empresaID", "nombre", "apellidos", "nombreUsuario", "contrasena",
            "email", "tieneCoche", "modeloCocheID", "puebloCiudad");

    private final CsvDataService csv;

    public UserCsvService(CsvDataService csv) {
        this.csv = csv;
    }

    public List<User> getAllUsers() {
        return csv.readRows(USERS_FILE).stream().map(this::toUser).toList();
    }

    public Optional<User> findById(long userId) {
        return getAllUsers().stream().filter(user -> user.userID() == userId).findFirst();
    }

    public Optional<User> findByNombreUsuario(String nombreUsuario) {
        return getAllUsers().stream()
                .filter(user -> user.nombreUsuario()
                        .equalsIgnoreCase(nombreUsuario == null ? "" : nombreUsuario.trim()))
                .findFirst();
    }

    public Optional<User> findByEmail(String email) {
        return getAllUsers().stream()
                .filter(user -> user.email().equalsIgnoreCase(email == null ? "" : email.trim()))
                .findFirst();
    }

    public User saveUser(User user) {
        csv.appendRow(USERS_FILE, USER_HEADERS, List.of(
                String.valueOf(user.userID()),
                String.valueOf(user.empresaID()),
                user.nombre(),
                user.apellidos(),
                user.nombreUsuario(),
                user.contrasena(),
                user.email(),
                String.valueOf(user.tieneCoche()),
                user.modeloCocheID(),
                user.puebloCiudad()));
        return user;
    }

    public long nextUserId() {
        return csv.nextId(USERS_FILE, "userID");
    }

    public List<Empresa> getCompanies() {
        return csv.readRows(COMPANIES_FILE).stream().map(row -> new Empresa(
                parseLong(row.get("empresaID")),
                row.getOrDefault("nombre", ""),
                row.getOrDefault("ciudad", ""),
                row.getOrDefault("descripcion", ""))).toList();
    }

    public Optional<Empresa> findCompany(long empresaID) {
        return getCompanies().stream().filter(company -> company.empresaID() == empresaID).findFirst();
    }

    public List<CarModel> getCarModels() {
        return csv.readRows(CARS_FILE).stream().map(row -> new CarModel(
                row.getOrDefault("modeloCocheID", ""),
                row.getOrDefault("marca", ""),
                row.getOrDefault("modelo", ""),
                row.getOrDefault("tipo", ""),
                parseDouble(row.get("emisionesKgKm")))).toList();
    }

    public Optional<CarModel> findCarModel(String modeloCocheID) {
        return getCarModels().stream()
                .filter(car -> car.modeloCocheID().equalsIgnoreCase(modeloCocheID == null ? "" : modeloCocheID))
                .findFirst();
    }

    public User updateProfile(ProfileUpdateRequest request) {
    List<Map<String, String>> rows = new ArrayList<>(csv.readRows(USERS_FILE));

    for (Map<String, String> row : rows) {
        if (parseLong(row.get("userID")) == request.userId()) {

            if (request.empresaID() != null) {
                row.put("empresaID", String.valueOf(request.empresaID()));
            }

            row.put("nombre", safe(request.nombre(), row.getOrDefault("nombre", "")));
            row.put("apellidos", safe(request.apellidos(), row.getOrDefault("apellidos", "")));
            row.put("email", safe(request.email(), row.getOrDefault("email", "")));
            row.put("puebloCiudad", safe(request.puebloCiudad(), row.getOrDefault("puebloCiudad", "")));

            boolean tieneCoche = request.tieneCoche() != null
                    ? request.tieneCoche()
                    : Boolean.parseBoolean(row.getOrDefault("tieneCoche", "false"));

            row.put("tieneCoche", String.valueOf(tieneCoche));

            row.put("modeloCocheID", tieneCoche
                    ? safe(request.modeloCocheID(), row.getOrDefault("modeloCocheID", "SIN_COCHE"))
                    : "SIN_COCHE"
            );

            csv.writeRows(USERS_FILE, USER_HEADERS, rows);

            return toUser(row);
        }
    }

    throw new IllegalArgumentException("Usuario no encontrado");
}

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private User toUser(Map<String, String> row) {
        return new User(
                parseLong(row.get("userID")),
                parseLong(row.get("empresaID")),
                row.getOrDefault("nombre", ""),
                row.getOrDefault("apellidos", ""),
                row.getOrDefault("nombreUsuario", ""),
                row.getOrDefault("contrasena", ""),
                row.getOrDefault("email", ""),
                Boolean.parseBoolean(row.getOrDefault("tieneCoche", "false")),
                row.getOrDefault("modeloCocheID", "SIN_COCHE"),
                row.getOrDefault("puebloCiudad", ""));
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
