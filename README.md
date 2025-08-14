## Graddie — AI-Powered Grading with a Simple Web UI

Graddie is a small, easy-to-run project that grades student work using a rubric and OpenAI.
You open a web page, choose an assignment, paste a submission, and get a score plus feedback.

Behind the scenes it uses Akka (for lightweight parallel workers) and calls OpenAI to help evaluate and generate feedback.

### What you can grade
- Assignment 1: MCQ (Multiple Choice) — uses strict answer matching and a rubric for scoring.
- Assignment 2: Short Answer — uses a reference guide, rubric-based scoring, and AI feedback.
- Assignment 3: Essay — uses the rubric categories to score overall quality and generate concise feedback.

All results are appended to `grading_results.csv` in the project root.

---

## Quick start (1 command)

```bash
# From project root:
export OPENAI_API_KEY="your-openai-api-key"   # required
./run_graddie.sh start                         # builds + starts coordinator, worker, and web
```

This opens `http://localhost:8080`.

To stop everything:
```bash
./run_graddie.sh stop
```

Useful helpers:
```bash
./run_graddie.sh status   # show process + port status
./run_graddie.sh logs     # tail logs
```

---

## How it works 

- Web UI: A single page served by the app where you pick an assignment and paste the student’s answer.
- Coordinator: The “manager” actor that loads the rubric, fans work out to workers, asks AI for feedback, and writes CSV results.
- Workers: The “helpers” that actually score a rubric category (e.g., Content, Organization). For MCQ they compute score by matching answers; for Short Answer and Essay they call OpenAI with clear prompts and the rubric.
- CSV Writer: Appends each result to `grading_results.csv`.

Flow when you click “Grade”: Web → Coordinator → Workers (+ OpenAI) → Coordinator → CSV → Web response.

---

## Detailed run instructions

### Requirements
- Java 17+
- Maven 3.6+
- An OpenAI API key set in your environment (or `.env` at project root)

### Environment setup
```bash
export OPENAI_API_KEY="your-openai-api-key"
# Or create a .env file in project root with: OPENAI_API_KEY="your-openai-api-key"
```

### Start everything via script (recommended)
```bash
./run_graddie.sh start
open http://localhost:8080
```

### Start manually (advanced)
Build + classpath:
```bash
mvn -q -DskipTests package
mvn -q -DincludeScope=runtime -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt
```

Start nodes:
```bash
# Coordinator (2553)
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2553 coordinator > coord_2553.out 2>&1 &

# Worker (2554)
nohup java -cp target/classes:$(cat cp.txt) com.agentic.GraddieMain 2554 worker > worker_2554.out 2>&1 &

# Web (8080)
nohup java -cp target/classes:$(cat cp.txt) com.agentic.WebServer > web.out 2>&1 &
open http://localhost:8080
```

Ports to check:
```bash
lsof -i :2553 -sTCP:LISTEN -n -P
lsof -i :2554 -sTCP:LISTEN -n -P
lsof -i :8080 -sTCP:LISTEN -n -P
```

Free ports if needed:
```bash
lsof -i :2553 -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | xargs -r kill -9
lsof -i :2554 -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | xargs -r kill -9
lsof -i :8080 -sTCP:LISTEN -n -P | awk 'NR>1 {print $2}' | xargs -r kill -9
```

Stop manually:
```bash
pkill -f 'com.agentic.GraddieMain 2553 coordinator' || true
pkill -f 'com.agentic.GraddieMain 2554 worker' || true
pkill -f com.agentic.WebServer || true
```

---

## Using the Web UI (what each assignment expects)

- Assignment 1 (MCQ):
  - The preview shows the questions. Enter answers as "Answer: A/B/C/D" under each question.
  - The system compares your selections to the reference and computes a strict score.
  - You’ll also see per-question breakdown feedback.

- Assignment 2 (Short Answer):
  - The preview shows a few prompts. Answer briefly.
  - The system uses a generous rubric and OpenAI to score and generate clear, constructive feedback.

- Assignment 3 (Essay):
  - Prompt: "Does technology bring people closer together, or does it push them further apart?"
  - Write a well-structured essay. The system scores rubric categories (e.g., Content, Organization, etc.) and returns concise, personalized feedback.

The preview box updates as soon as you pick the assignment in the dropdown.

---

## Where is the rubric and what does it do?

- File: `src/main/resources/final_rubric.csv`
- It defines categories (e.g., Content Quality, Organization, Critical Thinking, Mechanics) and the maximum points for each.
- Workers use these values to decide how much to award per category, and the app writes category-level scores and feedback into the CSV.

For MCQ and Short Answer, the first rubric item is used as the single scoring unit and normalized to 100. For Essay, all rubric items are summed.

---

## Outputs and logs

- Results CSV: `grading_results.csv` (appends a new row per graded submission)
- Logs: `logs/agentic-grader.log` plus `web.out`, `coord_2553.out`, `worker_2554.out`

---

## Configuration

Edit `src/main/resources/config.properties`:
- `openai.model`: model name (default: `gpt-4o-mini`)
- `openai.baseUrl`: change if using a compatible gateway

Env key resolution order: environment `OPENAI_API_KEY` → `.env` → config file.

### What to put in `config.properties`

Add or adjust these keys as needed (you can also keep the key in env instead of this file):

```properties
# Required if not using environment variable OPENAI_API_KEY
openai.api.key=YOUR_OPENAI_API_KEY

# Optional overrides
openai.model=gpt-4o-mini
openai.baseUrl=https://api.openai.com/v1

# (Optional) Tuning hints for prompts (some values are fixed in code)
# openai.max.tokens=1500
# openai.temperature=0.0
```

Notes:
- If `OPENAI_API_KEY` is set in your shell or `.env`, you can omit `openai.api.key` from this file.
- Keep the model to a fast, inexpensive option (e.g., `gpt-4o-mini`) unless you need a larger one.

---

## Troubleshooting

- Port already in use
  - Free 2553/2554/8080 using the commands above, then restart.

- UI not updating / preview stuck
  - Hard refresh (Cmd+Shift+R) or open a private window.
  - The server sends `Cache-Control: no-store` to avoid stale pages.

- API key errors
  - Confirm `echo $OPENAI_API_KEY` shows a real key.
  - Restart the processes after changing your environment.

- Check logs
  - `./run_graddie.sh logs` or tail `web.out`, `coord_2553.out`, `worker_2554.out`.

---

## API quick checks (optional)

MCQ (Assignment 1):
```bash
curl -sS -X POST http://localhost:8080/grade \
  -H 'Content-Type: application/json' \
  -d '{
        "studentId":"STU_DEMO",
        "assignment":"Assignment 1",
        "submission":"Question 1: ...\nAnswer: B\n\nQuestion 2: ...\nAnswer: C\n\nQuestion 3: ...\nAnswer: C"
      }'
```

Essay (Assignment 3):
```bash
curl -sS -X POST http://localhost:8080/grade \
  -H 'Content-Type: application/json' \
  -d '{
        "studentId":"STU_DEMO",
        "assignment":"Assignment 3",
        "submission":"<your short essay text>"
      }'
```

---

## Project layout (what to look at)

- `src/main/java/com/agentic/WebServer.java` — Web endpoints and HTML page.
- `src/main/java/com/agentic/GraddieMain.java` — Starts coordinator/worker cluster nodes.
- `src/main/java/com/agentic/actors/*` — Akka actors: Coordinator, Workers, Rubric reader, LLM feedback, CSV writer.
- `src/main/java/com/agentic/utils/*` — OpenAI client, CSV helpers, API key loader.
- `src/main/resources/*.conf` — Akka cluster config for nodes and web.
- `src/main/resources/final_rubric.csv` — The rubric used by workers.

That’s it — open the UI, pick an assignment, paste the submission, and grade!
