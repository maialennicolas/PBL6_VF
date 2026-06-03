package com.ecomove.service;

import com.ecomove.model.CarModel;
import com.ecomove.model.Empresa;
import com.ecomove.model.ProfileUpdateRequest;
import com.ecomove.model.User;
import com.ecomove.model.Usuario;
import com.ecomove.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserCsvService {

    // Mantenemos las rutas de empresas y coches porque siguen en CSV
    public static final String COMPANIES_FILE = "empresas.csv";
    public static final String CARS_FILE = "coches.csv";

    private final UsuarioRepository usuarioRepository;
    private final CsvDataService csv;

    // Spring inyectará automáticamente el repositorio y el servicio CSV restante
    public UserCsvService(UsuarioRepository usuarioRepository, CsvDataService csv) {
        this.usuarioRepository = usuarioRepository;
        this.csv = csv;
    }

    // ANTES: Leía todo el fichero de texto. AHORA: Va directo a la tabla de MySQL
    public List<Usuario> getAllUsers() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> findById(long userId) {
        return usuarioRepository.findById(userId);
    }

    public Optional<Usuario> findByNombreUsuario(String nombreUsuario) {
        if (nombreUsuario == null)
            return Optional.empty();
        return usuarioRepository.findByNombreUsuario(nombreUsuario.trim());
    }

    public Optional<Usuario> findByEmail(String email) {
        if (email == null)
            return Optional.empty();
        return usuarioRepository.findByEmail(email.trim());
    }

    // ANTES: Hacía un append en el archivo plano. AHORA: Hace un INSERT/UPDATE en
    // MySQL
    public Usuario saveUser(Usuario user) {
        return usuarioRepository.save(user);
    }

    // Ya no necesitas auto-calcular el ID de forma manual leyendo filas; MySQL lo
    // hará solo con el AutoIncrement
    public long nextUserId() {
        return 0; // Puedes dejarlo retornar 0, el ID real lo asignará la base de datos al guardar
    }

    // Estos métodos se quedan exactamente igual porque se alimentan de catálogos
    // fijos en CSV
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

    // Añade esto a tu UserCsvService.java
    public Usuario updateProfile(ProfileUpdateRequest request) {
        Usuario user = usuarioRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setNombre(request.nombre());
        user.setApellidos(request.apellidos());
        user.setPuebloCiudad(request.puebloCiudad());

        return usuarioRepository.save(user);
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