package com.ecomove.model;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios_ecomove")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private long userID;
    
    @Column(name = "empresaid")
    private long empresaID;
    
    private String nombre;
    private String apellidos;
    
    @Column(name = "nombre_usuario", unique = true)
    private String nombreUsuario;
    
    private String contrasena;
    
    @Column(unique = true)
    private String email;
    
    @Column(name = "tiene_coche")
    private boolean tieneCoche;
    
    @Column(name = "modelo_cocheid")
    private String modeloCocheID;
    
    @Column(name = "pueblo_ciudad")
    private String puebloCiudad;

    // =========================================================================
    // CONSTRUCTORES (Obligatorios para JPA y creación manual)
    // =========================================================================

    // Constructor vacío obligatorio para que Hibernate pueda instanciar la clase
    public Usuario() {
    }

    // Constructor lleno para cuando necesites crear un Usuario con todos los datos
    public Usuario(long userID, long empresaID, String nombre, String apellidos, String nombreUsuario, 
                   String contrasena, String email, boolean tieneCoche, String modeloCocheID, String puebloCiudad) {
        this.userID = userID;
        this.empresaID = empresaID;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
        this.email = email;
        this.tieneCoche = tieneCoche;
        this.modeloCocheID = modeloCocheID;
        this.puebloCiudad = puebloCiudad;
    }

    // =========================================================================
    // GETTERS AND SETTERS (Estándar de JavaBeans)
    // =========================================================================

    public long getUserID() {
        return userID;
    }

    public void setUserID(long userID) {
        this.userID = userID;
    }

    public long getEmpresaID() {
        return empresaID;
    }

    public void setEmpresaID(long empresaID) {
        this.empresaID = empresaID;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isTieneCoche() { // En booleanos el estándar usa 'is' en vez de 'get'
        return tieneCoche;
    }

    public void setTieneCoche(boolean tieneCoche) {
        this.tieneCoche = tieneCoche;
    }

    public String getModeloCocheID() {
        return modeloCocheID;
    }

    public void setModeloCocheID(String modeloCocheID) {
        this.modeloCocheID = modeloCocheID;
    }

    public String getPuebloCiudad() {
        return puebloCiudad;
    }

    public void setPuebloCiudad(String puebloCiudad) {
        this.puebloCiudad = puebloCiudad;
    }
}