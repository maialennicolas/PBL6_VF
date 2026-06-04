# EcoMove web simplificada con CSV

Proyecto Spring Boot con frontend HTML/CSS/JavaScript y backend Java por capas (`controller`, `service`, `model`).

## Ejecutar

```powershell
mvn spring-boot:run
```

La aplicación está configurada en:

```text
http://localhost:8082
```

Si quieres usar `8080`, cambia `server.port=8082` en `src/main/resources/application.properties`.

## Usuarios de prueba

```text
jonu / 123456
anez / 123456
mikele / 123456
leirea / 123456
```

Cada usuario tiene datos distintos porque el dashboard se calcula con su `userID`.

## CSV principales

Los datos persistentes están en la carpeta raíz:

```text
data/
├── usuarios.csv
├── empresas.csv
├── coches.csv
├── viajes.csv
├── recompensas.csv
├── canjeos.csv
├── lineas_transporte.csv
├── rutas_recomendadas.csv
├── ruta_pasos.csv
├── carpool_ofertas.csv
└── carpool_uniones.csv
```

## Campos del CSV de usuarios

```csv
userID,empresaID,nombre,apellidos,nombreUsuario,contrasena,email,tieneCoche,modeloCocheID,puebloCiudad
```

Al crear un usuario nuevo, se guarda una fila nueva en `data/usuarios.csv`.

## Datos que se guardan

- Registro de usuario: `data/usuarios.csv`
- Viaje finalizado desde la pantalla Bidaia: `data/viajes.csv`
- Oferta de karpoola: `data/carpool_ofertas.csv`
- Unión a un viaje de karpoola: `data/carpool_uniones.csv`
- Canjeo de recompensas: `data/canjeos.csv`

## Endpoints CSV para pasar datos a otro servicio

```text
GET /api/csv/users
GET /api/csv/trips
GET /api/csv/rewards
GET /api/csv/info
```

Ejemplo:

```text
http://localhost:8082/api/csv/users
```

## Matomo

El script de Matomo está preparado en:

```text
src/main/resources/static/index.html
```

Y los eventos se lanzan desde:

```text
src/main/resources/static/js/app.js
```

Ejemplos de eventos:

```javascript
matomoEvent('Auth', 'login', nombreUsuario);
matomoEvent('Tracking', 'stop', mode);
matomoEvent('Rewards', 'redeem', title);
matomoEvent('Carpool', 'offer', 'publish');
```

## Nota para Google Cloud Run

Para una demo local o entrega, CSV local está bien. En Cloud Run, los archivos locales del contenedor no son persistentes si el servicio se reinicia. Para producción, lo correcto sería guardar estos CSV en Cloud Storage o usar una base de datos.

## Datos reales de transporte

Se han sustituido los datos de transporte inventados por datos generados desde los tres ficheros `stops.txt` aportados:

- `ekialdebus_stops.txt`
- `euskotren_stops.txt`
- `bizkaibus_stops.txt`

Archivos generados dentro de `data/`:

- `lineas_transporte.csv`: resumen para la pantalla de Garraioa. Agrupa los datos por proveedor y zonas/municipios principales.
- `paradas_transporte.csv`: listado completo normalizado de paradas reales con proveedor, nombre, descripción, latitud, longitud, zona y municipio.

Endpoints útiles:

- `GET /api/transport-lines`
- `GET /api/transport-stops?proveedor=Ekialdebus&limit=20`
- `GET /api/transport-stops?proveedor=Euskotren&limit=20`
- `GET /api/transport-stops?proveedor=Bizkaibus&limit=20`
- `GET /api/csv/transport-lines`
- `GET /api/csv/transport-stops`

Nota: los TXT contenían paradas, no horarios ni líneas reales completas. Por eso `lineas_transporte.csv` es un resumen agrupado para que la pantalla actual pueda seguir funcionando, mientras que `paradas_transporte.csv` conserva la información real de paradas.
