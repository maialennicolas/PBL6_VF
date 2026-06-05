package com.ecomove.service;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Gestiona contraseñas de forma segura.
 *
 * Importante: las contraseñas no se "encriptan" de forma reversible; se guardan
 * como hash PBKDF2-SHA256 con salt aleatoria. En login se recalcula el hash y se compara.
 */
@Service
public class PasswordService {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private static final Pattern HAS_LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern HAS_UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_NUMBER = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private final SecureRandom random = new SecureRandom();

    public Optional<String> validateStrongPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return Optional.of("La contraseña es obligatoria");
        }
        if (rawPassword.length() < 12) {
            return Optional.of("La contraseña debe tener al menos 12 caracteres");
        }
        if (!HAS_LOWER.matcher(rawPassword).matches()) {
            return Optional.of("La contraseña debe incluir al menos una minúscula");
        }
        if (!HAS_UPPER.matcher(rawPassword).matches()) {
            return Optional.of("La contraseña debe incluir al menos una mayúscula");
        }
        if (!HAS_NUMBER.matcher(rawPassword).matches()) {
            return Optional.of("La contraseña debe incluir al menos un número");
        }
        if (!HAS_SPECIAL.matcher(rawPassword).matches()) {
            return Optional.of("La contraseña debe incluir al menos un carácter especial");
        }
        return Optional.empty();
    }

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public String hashIfNeeded(String passwordOrHash) {
        String value = passwordOrHash == null ? "" : passwordOrHash;
        String trimmed = value.trim();
        if (isHash(trimmed)) {
            return trimmed;
        }
        return hash(value);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (!isHash(storedPassword)) {
            // Compatibilidad temporal con usuarios antiguos guardados en texto plano.
            return MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
        }

        try {
            String[] parts = storedPassword.split("\\$");
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHash(String value) {
        return value != null && value.startsWith(PREFIX + "$") && value.split("\\$").length == 4;
    }

    public boolean shouldRehash(String storedPassword) {
        if (!isHash(storedPassword)) {
            return true;
        }
        try {
            String[] parts = storedPassword.split("\\$");
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (Exception e) {
            return true;
        }
    }

    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BYTES * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido calcular el hash de la contraseña", e);
        }
    }
}
