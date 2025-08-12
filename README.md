## Graddie — Super Simple Run Guide (Web + Cluster)

Akka-based grading with a web UI, powered by OpenAI. Defaults to model `gpt-4o-mini`.

### 1) Requirements
- Java 17+
- Maven 3.6+
- A valid OpenAI API key

### 2) Set your API key (recommended: environment)
```bash
export OPENAI_API_KEY="your-openai-api-key"
# or put it in a .env file at project root: OPENAI_API_KEY="your-openai-api-key"
```

### 3) Build once and generate classpath
```bash
mvn -q -DskipTests package
mvn -q -DincludeScope=runtime -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt
```

### 4) Start BOTH cluster nodes (coordinator + worker)
Ports used: coordinator 2553, worker 2554.
```bash
# Coordinator (2553)
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2553 coordinator > coord_2553.out 2>&1 &

# Worker (2554)
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2554 worker > worker_2554.out 2>&1 &
```

Verify both are listening:
```bash
lsof -i :2553 -sTCP:LISTEN -n -P
lsof -i :2554 -sTCP:LISTEN -n -P
```

If a port is busy, free it and retry:
```bash
lsof -i :2553 -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | xargs -r kill -9
lsof -i :2554 -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | xargs -r kill -9
```

### 5) Start the Web UI (joins the cluster)
```bash
nohup java -cp target/classes:$(cat cp.txt) com.agentic.WebServer > web.out 2>&1 &
open http://localhost:8080
```

That’s it. Submit a form in the browser to grade.

### 6) Quick test from terminal (optional)
```bash
curl -sS -X POST http://localhost:8080/grade \
  -H 'Content-Type: application/json' \
  -d '{
        "studentId":"1101",
        "assignment":"Assignment 3",
        "questionType":"ESSAY",
        "correctAnswers":"",
        "submission":"Short essay text here"
      }'
```

### 7) Stop everything
```bash
pkill -f 'com.agentic.GraddieMain 2553 coordinator' || true
pkill -f 'com.agentic.GraddieMain 2554 worker' || true
pkill -f com.agentic.WebServer || true
```

### Model and configuration
- Default model: `gpt-4o-mini` (see `src/main/resources/config.properties`).
- You can override with `openai.model=...` in `src/main/resources/config.properties`.
- API key resolution order: environment `OPENAI_API_KEY` → `.env` → `config.properties`.

### Files to know
- `src/main/resources/final_rubric.csv`: rubric
- `grading_results.csv`: results CSV
- `src/main/resources/web.conf`: WebServer cluster config
- `src/main/resources/node1.conf`, `node2.conf`: node configs (seed ports 2553/2554)

### Troubleshooting
- Port in use: free ports 2553/2554 using the commands above.
- Key issues (401): ensure `echo $OPENAI_API_KEY` shows a valid key; restart processes after changing env.
- Logs:
  - `tail -n 100 web.out | cat`
  - `tail -n 100 coord_2553.out | cat`
  - `tail -n 100 worker_2554.out | cat`
