-- Resetea las contraseñas de usuarios de demo a una contraseña fuerte.
-- Contraseña para todos: EcoMove2026!
-- Formato: PBKDF2-SHA256 con salt e iteraciones.

UPDATE usuarios_ecomove
SET contrasena = CASE nombre_usuario
    WHEN 'jonu' THEN 'pbkdf2_sha256$210000$t7ZJVpgu5R48OrtTWFRJVQ==$iSHoeZC8oaMUXlNqhqL9L6eP5qEzrsVCBTFZReXOpZo='
    WHEN 'anez' THEN 'pbkdf2_sha256$210000$NkhN+D3/X+iKXi0aSeRQqQ==$Rd8BSwzQkicRHjkigTWVKOt24yyYRm6rwAscnHrqqvw='
    WHEN 'inaki' THEN 'pbkdf2_sha256$210000$Juj839c1t5zFkkZ+PwAWMA==$W+6jFqCJPsdMap64FV4mUqHi3Vp/Qzivthu0LtJrSOY='
    ELSE contrasena
END
WHERE nombre_usuario IN ('jonu', 'anez', 'inaki');

-- Si quieres aplicar la misma contraseña fuerte a cualquier usuario que todavía esté en texto plano,
-- descomenta y adapta la siguiente línea:
-- UPDATE usuarios_ecomove SET contrasena = 'pbkdf2_sha256$210000$dEDsJLWu/sOY07Wzrq+OBg==$XiYUQZoukDX5XRDE95vZ/L7Pf7MKjXMRWdEJkGDrI5U=' WHERE contrasena NOT LIKE 'pbkdf2_sha256$%';
