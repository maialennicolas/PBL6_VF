#!/usr/bin/env bash
set -euo pipefail

CP="/app/target/classes:/app/target/dependency/*"

wait_for_rabbitmq() {
  echo "[SYCD] Esperando a RabbitMQ en ${RABBITMQ_HOST:-rabbitmq}:${RABBITMQ_PORT:-5672}..."
  for i in $(seq 1 60); do
    if (echo > "/dev/tcp/${RABBITMQ_HOST:-rabbitmq}/${RABBITMQ_PORT:-5672}") >/dev/null 2>&1; then
      echo "[SYCD] RabbitMQ listo."
      return 0
    fi
    sleep 2
  done
  echo "[SYCD] No se pudo conectar con RabbitMQ." >&2
  return 1
}

start_java() {
  local name="$1"
  shift
  echo "[SYCD] Arrancando ${name}: $*"
  java -cp "$CP" "$@" &
  PIDS+=("$!")
}

stop_all() {
  echo "[SYCD] Parando procesos..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" >/dev/null 2>&1 || true
  done
}
trap stop_all TERM INT EXIT

PIDS=()
wait_for_rabbitmq

java -cp "$CP" pbl6.arquitectura1.Publisher.KafkaStreamConfig || true

start_java "TaskWorker" pbl6.arquitectura1.Worker.TaskWorker
start_java "WorkerC" pbl6.arquitectura1.Worker.WorkerC
start_java "WorkerP" pbl6.arquitectura1.Worker.WorkerP
start_java "WorkerKP" pbl6.arquitectura1.Worker.WorkerKP
start_java "ResultWorker" pbl6.arquitectura1.Resultado.ResultWorker

wait -n "${PIDS[@]}"