## Graddie (Web + Cluster) — Quick Start

An Akka-based grading system with a web UI, using OpenAI (gpt-3.5-turbo).

### Requirements
- Java 17+
- Maven 3.6+
- OpenAI API key

### Set your API key (choose one)
- Environment: `export OPENAI_API_KEY="your-openai-api-key"`
- .env file in repo root: `OPENAI_API_KEY="your-openai-api-key"`
- Config file: add `openai.api.key=your-openai-api-key` to `src/main/resources/config.properties`

### Run the Web UI
```bash
mvn -q -DskipTests package
nohup java -cp target/classes:$(mvn -q -DincludeScope=runtime -DskipTests dependency:build-classpath -Dmdep.outputFile=/dev/stdout) com.agentic.WebServer > web.out 2>&1 &
# then open http://localhost:8080
```

### Optional: Run cluster nodes (for remote workers)
```bash
# Coordinator on 2551
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2551 coordinator > coord.out 2>&1 &

# Worker on 2552
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2552 worker > worker.out 2>&1 &
```

### Files to know
- `src/main/resources/final_rubric.csv`: rubric
- `grading_results.csv`: output CSV
- `src/main/resources/web.conf`: web server cluster config
- `src/main/resources/node1.conf`, `node2.conf`: node configs

### Notes
- Web UI joins the cluster and can use remote workers; if none exist, it uses local actors.
- Feedback is concise by design (short, actionable). Adjust in `OpenAIClient.buildOverallFeedbackPrompt` if needed.