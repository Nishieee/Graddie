#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

COORD_PORT=2553
WORKER_PORT=2554
WEB_PORT=8080

JAVA_CMD="java"
MVN_CMD="mvn"

usage() {
  cat <<EOF
Usage: $(basename "$0") <start|stop|status|logs>

Commands:
  start   Build and start coordinator ($COORD_PORT), worker ($WORKER_PORT), and web ($WEB_PORT)
  stop    Stop all Graddie processes
  status  Show process and port status
  logs    Tail the latest logs (Ctrl+C to exit)
EOF
}

require_tools() {
  command -v "$JAVA_CMD" >/dev/null 2>&1 || { echo "Error: java not found."; exit 1; }
  command -v "$MVN_CMD"  >/dev/null 2>&1 || { echo "Error: mvn not found."; exit 1; }
}

source_env() {
  # Prefer exported env; fallback to .env if present
  if [[ -z "${OPENAI_API_KEY:-}" && -f .env ]]; then
    # shellcheck disable=SC1091
    set +u; source .env; set -u || true
  fi
  if [[ -z "${OPENAI_API_KEY:-}" ]]; then
    echo "Warning: OPENAI_API_KEY not set. The app may run in mock mode."
  fi
}

build_project() {
  echo "==> Building project (skip tests)"
  "$MVN_CMD" -q -DskipTests package
  echo "==> Generating runtime classpath"
  "$MVN_CMD" -q -DincludeScope=runtime -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt
}

kill_on_port() {
  local port="$1"
  local pids
  pids=$(lsof -ti tcp:"$port" || true)
  if [[ -n "$pids" ]]; then
    echo "==> Freeing port $port (PIDs: $pids)"
    kill -9 $pids 2>/dev/null || true
  fi
}

start_nodes() {
  local cp
  cp="target/classes:$(cat cp.txt)"

  echo "==> Starting coordinator on $COORD_PORT"
  nohup "$JAVA_CMD" -cp "$cp" com.agentic.GraddieMain "$COORD_PORT" coordinator \
    > "$LOG_DIR/coord_${COORD_PORT}.out" 2>&1 & echo $! > "$LOG_DIR/coord_${COORD_PORT}.pid"

  echo "==> Starting worker on $WORKER_PORT"
  nohup "$JAVA_CMD" -cp "$cp" com.agentic.GraddieMain "$WORKER_PORT" worker \
    > "$LOG_DIR/worker_${WORKER_PORT}.out" 2>&1 & echo $! > "$LOG_DIR/worker_${WORKER_PORT}.pid"

  echo "==> Starting web server on http://localhost:${WEB_PORT}"
  nohup "$JAVA_CMD" -cp "$cp" com.agentic.WebServer \
    > "$LOG_DIR/web.out" 2>&1 & echo $! > "$LOG_DIR/web.pid"
}

wait_for_listen() {
  local port="$1"; local name="$2"; local tries=40; local delay=0.25
  echo -n "==> Waiting for $name to listen on :$port"
  while (( tries-- > 0 )); do
    if lsof -i :"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
      echo " ... up"
      return 0
    fi
    echo -n "."
    sleep "$delay"
  done
  echo "\nWarning: $name did not confirm listening on :$port (continuing)"
}

open_browser() {
  if command -v open >/dev/null 2>&1; then
    open "http://localhost:${WEB_PORT}" || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "http://localhost:${WEB_PORT}" || true
  fi
}

stop_all() {
  echo "==> Stopping processes by PID files (if present)"
  for f in "$LOG_DIR"/*.pid; do
    [[ -e "$f" ]] || continue
    pid=$(cat "$f" || true)
    if [[ -n "${pid:-}" && -d "/proc/$pid" || $(ps -p "$pid" -o pid= || true) ]]; then
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$f"
  done

  echo "==> Stopping any remaining java processes by class name"
  pkill -f 'com.agentic.GraddieMain 2553 coordinator' 2>/dev/null || true
  pkill -f 'com.agentic.GraddieMain 2554 worker' 2>/dev/null || true
  pkill -f 'com.agentic.WebServer' 2>/dev/null || true

  echo "==> Freeing ports"
  kill_on_port "$COORD_PORT"
  kill_on_port "$WORKER_PORT"
  kill_on_port "$WEB_PORT"
}

status_all() {
  echo "==> Process status"
  ps aux | grep -E 'com\.agentic\.(GraddieMain|WebServer)' | grep -v grep | cat
  echo
  echo "==> Port status"
  for p in "$COORD_PORT" "$WORKER_PORT" "$WEB_PORT"; do
    echo "-- Port $p --"
    lsof -i :"$p" -sTCP:LISTEN -n -P | cat || true
  done
}

tail_logs() {
  echo "==> Tailing logs (Ctrl+C to stop)"
  touch "$LOG_DIR/coord_${COORD_PORT}.out" "$LOG_DIR/worker_${WORKER_PORT}.out" "$LOG_DIR/web.out"
  tail -n 50 -F "$LOG_DIR/coord_${COORD_PORT}.out" \
               "$LOG_DIR/worker_${WORKER_PORT}.out" \
               "$LOG_DIR/web.out" | cat
}

cmd="${1:-start}"

case "$cmd" in
  start)
    require_tools
    source_env
    kill_on_port "$COORD_PORT"
    kill_on_port "$WORKER_PORT"
    kill_on_port "$WEB_PORT"
    build_project
    start_nodes
  wait_for_listen "$COORD_PORT" coordinator
  wait_for_listen "$WORKER_PORT" worker
  wait_for_listen "$WEB_PORT" web
    echo "==> Started. Logs in $LOG_DIR"
    status_all
    open_browser
    ;;
  stop)
    stop_all
    echo "==> Stopped."
    ;;
  status)
    status_all
    ;;
  logs)
    tail_logs
    ;;
  *)
    usage
    exit 1
    ;;
esac


