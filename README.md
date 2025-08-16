# Graddie — Distributed AI Grading System

A distributed grading system built with Akka Cluster that uses AI to grade student assignments. The system demonstrates actor-based distributed computing with multiple nodes, load balancing, and automated rubric-based evaluation.

## Project Summary

Graddie is an educational grading platform that showcases distributed systems principles using Akka Cluster. Students submit assignments through a web interface, and the system distributes grading tasks across multiple worker nodes that integrate with OpenAI's API for intelligent evaluation. The system processes three types of assignments (MCQ, Short Answer, Essay) using a configurable rubric and returns detailed feedback and scores.

## Why This Project

This project demonstrates key distributed systems concepts required for scalable applications:
- **Distributed Processing**: Shows how to break down complex tasks (grading) across multiple nodes
- **Load Balancing**: Automatically distributes work to available workers for optimal resource utilization
- **Fault Tolerance**: Akka Cluster provides resilience through node supervision and failure handling
- **Scalability**: Easy horizontal scaling by adding worker nodes without system downtime
- **Real-World Integration**: Demonstrates external API integration (OpenAI) in a distributed context
- **Actor Model**: Showcases message-passing concurrency patterns for reliable distributed communication

## Cluster Architecture

### Node Types
- **Coordinator Node** (Port 2553): Cluster seed node that manages task orchestration, rubric loading, result aggregation, and worker supervision
- **Worker Nodes** (Port 2554+): Compute nodes that process grading tasks, integrate with OpenAI API, and handle specific rubric categories
- **Web Server** (Port 8080): HTTP interface node that serves the web UI and handles REST API requests

### Cluster Features
- **Automatic Discovery**: Nodes automatically join the cluster using Akka's cluster membership
- **Load Distribution**: Work is distributed across available workers using Akka's router patterns
- **Dynamic Scaling**: New worker nodes can be added at runtime without affecting ongoing operations
- **Failure Handling**: Cluster detects node failures and redistributes work to healthy nodes

## Persistence Strategy

The system uses **file-based persistence** for simplicity and demonstration purposes:
- **CSV Results Storage**: All grading results are persisted to `grading_results.csv` with detailed category-level scores and feedback
- **Rubric Configuration**: Rubric definitions stored in `final_rubric.csv` allow for configurable grading criteria
- **Concurrent Write Management**: ResultWriterActor handles concurrent access to result files using actor message serialization
- **Structured Output**: Results include student ID, assignment type, scores, feedback, and timestamps for comprehensive tracking
- **Append-Only Design**: New results are appended to preserve historical grading data

*Note: For production use, this could be extended to database persistence (PostgreSQL, MongoDB) using Akka Persistence for event sourcing and state recovery.*

## Actor Functionality

### GradingCoordinatorActor
- **Role**: Orchestrates the grading workflow and manages cluster coordination
- **Responsibilities**: 
  - Loads rubric configuration from CSV files
  - Distributes grading tasks to worker pool using round-robin routing
  - Aggregates results from multiple workers
  - Coordinates with ResultWriterActor for persistence
  - Implements ASK pattern for capacity checks before task distribution

### GradingWorkerActor
- **Role**: Processes individual grading tasks with AI integration
- **Responsibilities**:
  - Evaluates specific rubric categories (Content, Organization, Critical Thinking, Mechanics)
  - Integrates with OpenAI API for intelligent scoring and feedback generation
  - Handles different assignment types (MCQ, Short Answer, Essay) with specialized logic
  - Implements TELL pattern for sending results back to coordinator
  - Provides worker health status and capacity information

### SubmissionReceiverActor
- **Role**: Handles incoming student submissions and routes them through the system
- **Responsibilities**:
  - Receives student submissions from the web interface
  - Validates submission format and content
  - Implements FORWARD pattern to route submissions to coordinator while preserving sender context
  - Tracks submission metadata for result correlation

### LLMActor
- **Role**: Manages LLM integration and overall feedback generation
- **Responsibilities**:
  - Generates comprehensive feedback by synthesizing category-level evaluations
  - Manages OpenAI API connections and rate limiting
  - Implements FORWARD pattern for delegating complex feedback generation to helper actors
  - Handles LLM request failures and retry logic

### ResultWriterActor
- **Role**: Persists grading results and manages output formatting
- **Responsibilities**:
  - Writes grading results to CSV files with proper formatting
  - Implements FORWARD pattern for delegating CSV operations to helper actors
  - Manages file locking and concurrent write operations
  - Generates structured output with category-level details

### RubricReaderActor
- **Role**: Loads and manages grading rubric configuration
- **Responsibilities**:
  - Reads rubric definitions from CSV configuration files
  - Validates rubric structure and scoring bands
  - Provides rubric data to workers for consistent grading
  - Handles rubric updates and reconfiguration

### LoggerActor
- **Role**: Manages distributed logging across the cluster
- **Responsibilities**:
  - Centralizes log message collection from all cluster nodes
  - Formats and structures log output for debugging and monitoring
  - Handles log message routing and persistence
  - Provides cluster-wide logging coordination and log level management

## Most Important Implementation Details

### Out-of-the-Box Distributed Systems Features
- **Complete Akka Cluster Implementation**: Full cluster setup with seed nodes, member discovery, and failure detection
- **Three Communication Patterns**: 
  - **TELL Pattern**: Fire-and-forget messaging (GradingWorkerActor → Coordinator)
  - **ASK Pattern**: Request-response with futures (capacity checks, health monitoring)
  - **FORWARD Pattern**: Message delegation with sender preservation (SubmissionReceiver, LLMActor, ResultWriter)
- **Load Balancing Router**: Automatic work distribution using Akka's router with round-robin strategy
- **Horizontal Scaling**: Runtime addition of worker nodes without downtime
- **External Integration**: OpenAI API integration across distributed workers with error handling
- **Concurrent State Management**: Thread-safe result aggregation and CSV writing using actor model
- **Configuration Management**: Distributed configuration loading across cluster nodes
- **Health Monitoring**: Worker capacity checking and cluster health status

### Advanced Implementation Features
- **Multiple Assignment Types**: Different processing logic for MCQ, Short Answer, and Essay assignments
- **Rubric-Based Evaluation**: Configurable scoring criteria loaded from CSV with category-level feedback
- **Interactive Demo Script**: `demo_distribution.sh` showcases scaling, load balancing, and communication patterns
- **Comprehensive Logging**: Distributed logging across all nodes with structured output
- **REST API**: Full HTTP interface for programmatic access beyond web UI
- **Error Recovery**: Graceful handling of API failures, network issues, and node failures

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- OpenAI API key

### Running the System
```bash
# Set your API key
export OPENAI_API_KEY="your-openai-api-key"

# Start the distributed cluster
./run_graddie.sh start

# Access the web interface
open http://localhost:8080
```

### Management Commands
```bash
./run_graddie.sh stop     # Stop all nodes
./run_graddie.sh status   # Check cluster status
./run_graddie.sh logs     # View logs
```

## Architecture

The system uses **Akka Cluster** with three types of nodes:

- **Web Server** (Port 8080): HTTP interface and user interaction
- **Coordinator** (Port 2553): Task orchestration and cluster management  
- **Workers** (Port 2554+): Distributed processing and LLM integration

**Message Flow**: Web → Coordinator → Workers → OpenAI → Results → CSV

## Assignment Types

1. **MCQ**: Multiple choice with strict answer matching
2. **Short Answer**: Reference-based scoring with AI feedback
3. **Essay**: Comprehensive rubric evaluation

## Demonstration

### Distribution Demo
Run the interactive demonstration to see scaling and load balancing:
```bash
./demo_distribution.sh
```

This demonstrates:
- Multi-node cluster formation
- Horizontal scaling with multiple workers
- Load balancing across nodes
- Akka communication patterns (TELL, ASK, FORWARD)

### Manual Scaling
Add additional worker nodes:
```bash
# Add more workers on different ports
java -cp target/classes:$(cat target/classpath.txt) com.agentic.GraddieMain 2555 worker
java -cp target/classes:$(cat target/classpath.txt) com.agentic.GraddieMain 2556 worker
```

## Configuration

### API Key Setup
```bash
# Environment variable (recommended)
export OPENAI_API_KEY="your-openai-api-key"

# Or create .env file in project root
echo "OPENAI_API_KEY=your-openai-api-key" > .env
```

### System Configuration
Edit `src/main/resources/config.properties`:
```properties
openai.model=gpt-4o-mini
openai.baseUrl=https://api.openai.com/v1
```

## Output

- **Results**: `grading_results.csv` (CSV file with all grading results)
- **Logs**: `logs/` directory with node-specific log files

## API Usage

Test the system programmatically:
```bash
curl -X POST http://localhost:8080/grade \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": "demo_student",
    "assignment": "Assignment 1", 
    "submission": "Answer: A\nAnswer: B\nAnswer: C"
  }'
```

## Project Structure

```
src/main/java/com/agentic/
├── WebServer.java           # HTTP server and web interface
├── GraddieMain.java         # Cluster node starter
├── actors/                  # Akka actor implementations
│   ├── GradingCoordinatorActor.java
│   ├── GradingWorkerActor.java
│   └── ...
└── utils/                   # Utilities and integrations
    ├── OpenAIClient.java
    └── ...
```
