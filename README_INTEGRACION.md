# EcoMove · proyecto contenedor WEB + SYCD + Matomo

Este proyecto junta los dos proyectos:

- `web/`: aplicación Spring Boot + HTML/CSS/JS.
- `sycd/`: workers Java de Sistemas Concurrentes y Distribuidos.
- `data/`: CSV compartidos por WEB y SYCD.
- `docker-compose.yml`: levanta WEB, SYCD, RabbitMQ, Matomo y MariaDB.

## Cómo arrancarlo

Desde la carpeta `ecomove-contenedor`:

```bash
docker compose up --build
```

Servicios:

- Web EcoMove: <http://localhost:8083>
- Matomo: <http://localhost:8081>
- RabbitMQ Management: <http://localhost:15672> (`guest` / `guest`)

## Flujo real WEB -> SYCD -> WEB

La integración ya no funciona como simulación ni como lectura periódica de viajes pendientes. Ahora la web manda eventos reales a SYCD mientras el usuario está haciendo la `bidaia`.

1. La web crea/lee usuarios desde `data/usuarios.csv`.
2. Cuando el usuario pulsa iniciar viaje, la web:
   - crea una `sessionID`,
   - guarda el primer punto en `data/ubicaciones_bidaia.csv`,
   - publica inmediatamente un evento `START` en RabbitMQ para SYCD.
3. Cada vez que la web guarda un punto GPS en `/api/tracking/location`, también publica ese punto en RabbitMQ con:
   - `userID`,
   - `empresaID`,
   - `sessionID`,
   - latitud/longitud,
   - velocidad estimada,
   - metros acumulados,
   - timestamp real del navegador.
4. Cuando el usuario pulsa parar, la web:
   - crea el viaje en `data/viajes.csv` con `estadoCalculo=PROCESANDO_SYCD`,
   - publica el evento final `END` a SYCD.
5. `TaskWorker` mantiene el estado del recorrido por `userID + sessionID` y, al recibir `END`, envía el resumen a los workers.
6. Los workers calculan/clasifican el transporte:
   - `WorkerC`: coche.
   - `WorkerP`: andando/corriendo/bici/patinete.
   - `WorkerKP`: bus/tren si está cerca de paradas.
7. `ResultWorker` recibe la clasificación final y actualiza `data/viajes.csv` con:
   - `modo`,
   - `km`,
   - `co2`,
   - `puntos`,
   - `icono`,
   - `estadoCalculo=CALCULADO`.
8. La web lee ese mismo CSV y muestra el resultado actualizado en viajes, dashboard, estadísticas, puntos y reparto de transporte.

## CSV compartidos

El volumen importante es:

```yaml
./data:/app/data
```

Por eso WEB y SYCD ven los mismos CSV. La web guarda usuarios, puntos GPS y viajes; SYCD escribe el resultado final del cálculo en `viajes.csv`.

## RabbitMQ

La web y SYCD usan la misma cola `tarea` dentro del exchange `stream_garraioa`.

Variables usadas por los contenedores:

```yaml
RABBITMQ_HOST: rabbitmq
RABBITMQ_PORT: 5672
RABBITMQ_USER: guest
RABBITMQ_PASS: guest
```

## Matomo

Matomo está añadido en `docker-compose.yml` con MariaDB.

La web carga el script de Matomo desde:

```text
http://localhost:8081/
```

con `siteId=1`, configurado en:

```text
web/src/main/resources/static/js/config.js
```

La primera vez debes entrar en <http://localhost:8081> y terminar el asistente de instalación de Matomo. Crea el primer sitio para la web EcoMove; normalmente quedará como ID `1`. Si Matomo te da otro ID, cambia `ECOMOVE_MATOMO_SITE_ID` en `config.js`.

## Cambios principales

### Web

- Añadida clase `RabbitMqTripPublisher`.
- Añadida dependencia `com.rabbitmq:amqp-client` al `pom.xml` de la web.
- `/api/tracking/start` guarda el primer punto y lo manda a SYCD.
- `/api/tracking/location` guarda cada punto y lo manda a SYCD en tiempo real.
- `/api/tracking/stop` crea el viaje en `viajes.csv`, manda el evento final a SYCD y deja el viaje en `PROCESANDO_SYCD`.
- Si la web no puede enviar el evento final a RabbitMQ, el viaje queda marcado como `ERROR_ENVIO_SYCD`.
- Se calcula velocidad estimada si el navegador no manda `coords.speed`.

### SYCD

- `TaskWorker` ya usa el timestamp enviado por la web, no solo el tiempo de recepción del mensaje.
- `TaskWorker` conserva `sessionID`, para devolver el resultado al viaje correcto.
- `WorkerC`, `WorkerP` y `WorkerKP` reenvían `sessionID` con la clasificación.
- `ResultWorker` actualiza `data/viajes.csv` con el resultado final.
- `CsvTripPublisher` queda en el código como utilidad antigua/fallback manual, pero ya no se arranca en `docker-entrypoint.sh`. El flujo principal es en tiempo real desde la web.

## Prueba rápida

1. Arranca todo:

```bash
docker compose up --build
```

2. Entra en la web: <http://localhost:8083>
3. Haz login con un usuario existente, por ejemplo `jonu / 123456`.
4. Inicia una `bidaia`.
5. Mira que la web va guardando puntos en `data/ubicaciones_bidaia.csv`.
6. Mira logs de SYCD:

```bash
docker compose logs -f sycd
```

7. Para la `bidaia`.
8. Comprueba `data/viajes.csv`: el viaje debería pasar de `PROCESANDO_SYCD` a `CALCULADO`.

También puedes mirar RabbitMQ en <http://localhost:15672> para ver las colas `tarea`, `kotxea`, `publikoa`, `k_p` y `emaitza`.
