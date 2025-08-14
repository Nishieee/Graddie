#!/usr/bin/env bash
set -euo pipefail

# Graddie: Automated distribution demo
# - Baseline: Web server only
# - Distributed: Add coordinator (2553) + worker (2554)
# - Measures requests/sec and latency (uses 'hey' if available, else curl-based)

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
CP_FILE="$ROOT_DIR/cp.txt"
RESULTS_CSV="$ROOT_DIR/grading_results.csv"
WEB_OUT="$ROOT_DIR/web.out"
COORD_OUT="$ROOT_DIR/coord_2553.out"
WORKER_OUT="$ROOT_DIR/worker_2554.out"

PAYLOAD_MCQ="/tmp/graddie_payload_mcq.json"
LOG_FILE="/tmp/graddie_demo.log"

REQS=100
CONC=20

info() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*"; }
err()  { echo "[ERROR] $*" >&2; }

require_built() {
  if [[ ! -f "$ROOT_DIR/target/classes" ]]; then
    return 1
  fi
}

build_once() {
  info "Building project (skipTests) …"
  (cd "$ROOT_DIR" && mvn -q -DskipTests package)
  info "Generating runtime classpath …"
  (cd "$ROOT_DIR" && mvn -q -DincludeScope=runtime -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt)
}

kill_if_listening() {
  local port="$1"
  local pids
  pids=$(lsof -i :"$port" -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | sort -u || true)
  if [[ -n "$pids" ]]; then
    warn "Freeing port $port (PIDs: $pids) …"
    echo "$pids" | xargs -r kill -9 || true
  fi
}

stop_all() {
  info "Stopping any running Graddie processes …"
  pkill -f 'com.agentic.WebServer' || true
  pkill -f 'com.agentic.GraddieMain 2553 coordinator' || true
  pkill -f 'com.agentic.GraddieMain 2554 worker' || true
  sleep 1
  kill_if_listening 8080
  kill_if_listening 2553
  kill_if_listening 2554
}

wait_for_http() {
  local url="$1"; local tries=60
  for i in $(seq 1 $tries); do
    if curl -fsS "$url" > /dev/null; then return 0; fi
    sleep 1
  done
  return 1
}

start_web() {
  info "Starting Web server (port 8080) …"
  nohup java -cp "$ROOT_DIR/target/classes:$(cat "$CP_FILE")" com.agentic.WebServer > "$WEB_OUT" 2>&1 &
  sleep 2
  if ! wait_for_http http://localhost:8080/; then
    err "Web server did not start in time. See $WEB_OUT"
    exit 1
  fi
  info "Web server is online at http://localhost:8080"
}

start_coordinator() {
  info "Starting Coordinator node (port 2553) …"
  nohup java -cp "$ROOT_DIR/target/classes:$(cat "$CP_FILE")" com.agentic.GraddieMain 2553 coordinator > "$COORD_OUT" 2>&1 &
  sleep 2
}

start_worker_2554() {
  info "Starting Worker node (port 2554) …"
  nohup java -cp "$ROOT_DIR/target/classes:$(cat "$CP_FILE")" com.agentic.GraddieMain 2554 worker > "$WORKER_OUT" 2>&1 &
  sleep 2
}

prepare_payloads() {
  cat > "$PAYLOAD_MCQ" << 'EOF'
{
  "studentId": "STU_DEMO",
  "assignment": "Assignment 1",
  "submission": "Question 1: What is 5 + 3?\nA. 6\nB. 8\nC. 10\nD. 9\nAnswer: B\n\nQuestion 2: Which number is even?\nA. 7\nB. 5\nC. 4\nD. 9\nAnswer: C\n\nQuestion 3: What is 10 - 6?\nA. 2\nB. 3\nC. 4\nD. 5\nAnswer: C"
}
EOF
}

run_load_with_hey() {
  local label="$1"
  info "[${label}] Running load with hey: -n $REQS -c $CONC …"
  hey -m POST -T application/json -D "$PAYLOAD_MCQ" -n "$REQS" -c "$CONC" http://localhost:8080/grade | tee -a "$LOG_FILE"
}

run_load_with_curl() {
  local label="$1"
  info "[${label}] Running load with curl concurrency: -n $REQS -c $CONC …"
  local per_client=$(( (REQS + CONC - 1) / CONC ))
  local start_ts=$(date +%s)
  pids=()
  for i in $(seq 1 "$CONC"); do
    (
      for j in $(seq 1 "$per_client"); do
        curl -sS -X POST http://localhost:8080/grade \
          -H 'Content-Type: application/json' \
          -d @"$PAYLOAD_MCQ" > /dev/null || true
      done
    ) &
    pids+=($!)
  done
  for p in "${pids[@]}"; do wait "$p" || true; done
  local end_ts=$(date +%s)
  local elapsed=$(( end_ts - start_ts ))
  if [[ "$elapsed" -eq 0 ]]; then elapsed=1; fi
  local rps=$(( REQS * 3600 / elapsed ))
  info "[${label}] Completed $REQS requests in ${elapsed}s → approx ${rps} reqs/hour"
}

count_results() {
  if [[ -f "$RESULTS_CSV" ]]; then
    # subtract header line if present
    local lines
    lines=$(wc -l < "$RESULTS_CSV" | tr -d ' ')
    local entries=$(( lines > 1 ? lines - 1 : 0 ))
    echo "$entries"
  else
    echo "0"
  fi
}

print_tail() {
  info "Tail logs: web.out"
  tail -n 20 "$WEB_OUT" | cat || true
  info "Tail logs: worker_2554.out (if running)"
  tail -n 20 "$WORKER_OUT" | cat || true
}

main() {
  : > "$LOG_FILE"
  prepare_payloads

  stop_all
  build_once

  info "Starting BASELINE (Web only) …"
  start_web
  local before_baseline
  before_baseline=$(count_results)

  if command -v hey >/dev/null 2>&1; then
    run_load_with_hey "BASELINE"
  else
    warn "'hey' not found. Falling back to curl-based load generator. Install with: brew install hey"
    run_load_with_curl "BASELINE"
  fi

  local after_baseline
  after_baseline=$(count_results)
  info "Baseline results added: $(( after_baseline - before_baseline )) rows (total: $after_baseline)"
  print_tail

  info "Starting DISTRIBUTED (Coordinator + Worker) …"
  start_coordinator
  start_worker_2554
  sleep 3

  local before_dist
  before_dist=$(count_results)

  if command -v hey >/dev/null 2>&1; then
    run_load_with_hey "DISTRIBUTED"
  else
    run_load_with_curl "DISTRIBUTED"
  fi

  local after_dist
  after_dist=$(count_results)
  info "Distributed results added: $(( after_dist - before_dist )) rows (total: $after_dist)"
  print_tail

  info "Done. Summary:"
  info "- Baseline total rows after run: $after_baseline"
  info "- Distributed total rows after run: $after_dist"
  info "Check logs: $WEB_OUT, $COORD_OUT, $WORKER_OUT"
  info "To stop everything: pkill -f com.agentic.WebServer; pkill -f 'com.agentic.GraddieMain 2553 coordinator'; pkill -f 'com.agentic.GraddieMain 2554 worker'"
}

main "$@"


