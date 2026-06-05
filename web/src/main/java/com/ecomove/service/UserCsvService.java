package com.ecomove.service;

import com.ecomove.model.CarModel;
import com.ecomove.model.Empresa;
import com.ecomove.model.ProfileUpdateRequest;
import com.ecomove.model.User;
import com.ecomove.model.Usuario;
import com.ecomove.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de usuarios.
 *
 * Usuarios: base de datos MySQL/MariaDB mediante JPA.
 * Catálogos estáticos: CSV, porque empresas/coches siguen siendo catálogos de apoyo.
 *
 * Además mantiene data/usuarios.csv sincronizado para que el módulo SYCD, que todavía
 * se ejecuta fuera de Spring, pueda resolver el coche real del usuario al calcular CO₂.
 */
@Service
public class UserCsvService {

    public static final String USERS_FILE = "usuarios.csv";
    public static final String COMPANIES_FILE = "empresas.csv";
    public static final String CARS_FILE = "coches.csv";

    private static final List<String> USER_HEADERS = List.of(
            "userID", "empresaID", "nombre", "apellidos", "nombreUsuario", "contrasena", "email",
            "tieneCoche", "modeloCocheID", "puebloCiudad");

    private final UsuarioRepository usuarioRepository;
    private final CsvDataService csv;
    private final PasswordService passwordService;

    public UserCsvService(UsuarioRepository usuarioRepository, CsvDataService csv, PasswordService passwordService) {
        this.usuarioRepository = usuarioRepository;
        this.csv = csv;
        this.passwordService = passwordService;
    }

    /**
     * Al arrancar por primera vez, importa los usuarios del CSV antiguo a la BD.
     * Esto evita que la web se quede sin usuarios de prueba al cambiar a base de datos.
     */
    @PostConstruct
    public void bootstrapUsersFromCsvIfNeeded() {
        try {
            if (usuarioRepository.count() > 0) {
                mirrorUsersCsv();
                return;
            }

            List<Map<String, String>> rows = csv.readRows(USERS_FILE);
            if (rows.isEmpty()) {
                seedFallbackUsers();
            } else {
                for (Map<String, String> row : rows) {
                    Usuario usuario = new Usuario();
                    long id = parseLong(row.get("userID"));
                    if (id > 0) {
                        usuario.setUserID(id);
                    }
                    usuario.setEmpresaID(parseLong(row.get("empresaID")));
                    usuario.setNombre(row.getOrDefault("nombre", ""));
                    usuario.setApellidos(row.getOrDefault("apellidos", ""));
                    usuario.setNombreUsuario(row.getOrDefault("nombreUsuario", ""));
                    usuario.setContrasena(passwordService.hashIfNeeded(row.getOrDefault("contrasena", "")));
                    usuario.setEmail(row.getOrDefault("email", usuario.getNombreUsuario() + "@ecomove.local"));
                    usuario.setTieneCoche(Boolean.parseBoolean(row.getOrDefault("tieneCoche", "false")));
                    usuario.setModeloCocheID(row.getOrDefault("modeloCocheID", "SIN_COCHE"));
                    usuario.setPuebloCiudad(row.getOrDefault("puebloCiudad", ""));

                    if (!usuario.getNombreUsuario().isBlank()) {
                        usuarioRepository.save(usuario);
                    }
                }
            }

            mirrorUsersCsv();
            System.out.println("[UserService] Usuarios cargados desde CSV a la base de datos: " + usuarioRepository.count());
        } catch (Exception e) {
            System.err.println("[UserService] No se han podido inicializar usuarios desde CSV: " + e.getMessage());
        }
    }

    private void seedFallbackUsers() {
        String demoPassword = passwordService.hash("EcoMove2026!");
        Usuario jon = new Usuario(0, 101, "Jon", "Urrutia", "jonu", demoPassword, "jonu@ecomove.eus", true, "TESLA_MODEL_3", "Bilbo");
        Usuario ane = new Usuario(0, 101, "Ane", "Zabala", "anez", demoPassword, "anez@ecomove.eus", false, "SIN_COCHE", "Getxo");
        usuarioRepository.save(jon);
        usuarioRepository.save(ane);
    }

    public List<User> getAllUsers() {
        return usuarioRepository.findAll().stream()
                .sorted(Comparator.comparingLong(Usuario::getUserID))
                .map(this::toUser)
                .toList();
    }

    public Optional<User> findById(long userId) {
        return usuarioRepository.findById(userId).map(this::toUser);
    }

    public Optional<User> findByNombreUsuario(String nombreUsuario) {
        if (nombreUsuario == null) return Optional.empty();
        return usuarioRepository.findByNombreUsuario(nombreUsuario.trim()).map(this::toUser);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return usuarioRepository.findByEmail(email.trim()).map(this::toUser);
    }

    public User saveUser(User user) {
        Usuario entity = user.userID() > 0
                ? usuarioRepository.findById(user.userID()).orElseGet(Usuario::new)
                : new Usuario();

        applyUser(entity, user);
        Usuario saved = usuarioRepository.save(entity);
        mirrorUsersCsv();
        return toUser(saved);
    }

    public void updatePasswordHash(long userId, String rawPassword) {
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        user.setContrasena(passwordService.hash(rawPassword));
        usuarioRepository.save(user);
        mirrorUsersCsv();
    }

    /**
     * Ya no se calcula ID manualmente: lo asigna la base de datos.
     */
    public long nextUserId() {
        return 0L;
    }

    public User updateProfile(ProfileUpdateRequest request) {
        Usuario user = usuarioRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (request.empresaID() != null) user.setEmpresaID(request.empresaID());
        if (request.nombre() != null) user.setNombre(request.nombre());
        if (request.apellidos() != null) user.setApellidos(request.apellidos());
        if (request.email() != null) user.setEmail(request.email());
        if (request.puebloCiudad() != null) user.setPuebloCiudad(request.puebloCiudad());
        if (request.tieneCoche() != null) {
            user.setTieneCoche(request.tieneCoche());
            user.setModeloCocheID(request.tieneCoche()
                    ? safe(request.modeloCocheID(), "SIN_COCHE")
                    : "SIN_COCHE");
        } else if (request.modeloCocheID() != null) {
            user.setModeloCocheID(request.modeloCocheID());
        }

        Usuario saved = usuarioRepository.save(user);
        mirrorUsersCsv();
        return toUser(saved);
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
        if (modeloCocheID == null) return Optional.empty();
        return getCarModels().stream()
                .filter(car -> car.modeloCocheID().equalsIgnoreCase(modeloCocheID.trim()))
                .findFirst();
    }

    private void mirrorUsersCsv() {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            for (User user : getAllUsers()) {
                rows.add(Map.of(
                        "userID", String.valueOf(user.userID()),
                        "empresaID", String.valueOf(user.empresaID()),
                        "nombre", safe(user.nombre(), ""),
                        "apellidos", safe(user.apellidos(), ""),
                        "nombreUsuario", safe(user.nombreUsuario(), ""),
                        "contrasena", safe(user.contrasena(), ""),
                        "email", safe(user.email(), ""),
                        "tieneCoche", String.valueOf(user.tieneCoche()),
                        "modeloCocheID", safe(user.modeloCocheID(), "SIN_COCHE"),
                        "puebloCiudad", safe(user.puebloCiudad(), "")
                ));
            }
            csv.writeRows(USERS_FILE, USER_HEADERS, rows);
        } catch (Exception e) {
            System.err.println("[UserService] No se ha podido sincronizar usuarios.csv: " + e.getMessage());
        }
    }

    private void applyUser(Usuario entity, User user) {
        entity.setEmpresaID(user.empresaID());
        entity.setNombre(safe(user.nombre(), ""));
        entity.setApellidos(safe(user.apellidos(), ""));
        entity.setNombreUsuario(safe(user.nombreUsuario(), ""));
        entity.setContrasena(passwordService.hashIfNeeded(safe(user.contrasena(), "")));
        entity.setEmail(safe(user.email(), user.nombreUsuario() + "@ecomove.local"));
        entity.setTieneCoche(user.tieneCoche());
        entity.setModeloCocheID(user.tieneCoche() ? safe(user.modeloCocheID(), "SIN_COCHE") : "SIN_COCHE");
        entity.setPuebloCiudad(safe(user.puebloCiudad(), ""));
    }

    private User toUser(Usuario usuario) {
        return new User(
                usuario.getUserID(),
                usuario.getEmpresaID(),
                safe(usuario.getNombre(), ""),
                safe(usuario.getApellidos(), ""),
                safe(usuario.getNombreUsuario(), ""),
                safe(usuario.getContrasena(), ""),
                safe(usuario.getEmail(), ""),
                usuario.isTieneCoche(),
                safe(usuario.getModeloCocheID(), "SIN_COCHE"),
                safe(usuario.getPuebloCiudad(), ""));
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null || value.isBlank() ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value == null || value.isBlank() ? "0" : value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
