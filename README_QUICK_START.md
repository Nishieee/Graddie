# 🚀 Graddie Quick Start

## What is Graddie?
An AI-powered grading system that automatically grades student submissions using GPT-3.5-turbo.

## 🛠️ Setup

### 1. Check Dependencies
```bash
java -version  # Need Java 17+
mvn -version   # Need Maven 3.6+
```

### 2. Set OpenAI API Key
```bash
# Option A: Environment variable
export OPENAI_API_KEY="your-openai-api-key-here"

# Option B: .env file in project root
echo 'OPENAI_API_KEY="your-openai-api-key-here"' > .env

# Option C: config.properties
echo 'openai.api.key=your-openai-api-key-here' >> src/main/resources/config.properties
```
*Note: Without an API key, the system uses a mock client with placeholder feedback*

### 3. Build and Run
```bash
# Build the project
mvn clean compile

# Start the web server
mvn exec:java@web-server
```

## 🎯 How to Use

### Web Interface
- Open your browser to: http://localhost:8080
- Fill out the grading form with student details and submission
- Get instant AI-powered grading results with detailed feedback
- Download results as text files

## 📝 How the System Works

1. **Web Interface:**
   - Interactive form for grading submissions
   - Real-time AI-powered evaluation
   - Downloadable results with detailed feedback
   - Support for Essay, MCQ, and Short Answer questions

2. **Grading Process:**
   - Loads rubric from `src/main/resources/final_rubric.csv`
   - Evaluates 4 categories: Content, Organization, Critical Thinking, Mechanics
   - Generates AI-powered feedback using GPT-3.5-turbo
   - Saves results automatically

## 📁 Key Files
- `src/main/resources/final_rubric.csv` - Grading criteria
- `grading_results.csv` - Output results (auto-generated)
- `src/main/java/com/agentic/WebServer.java` - Web interface implementation

## 🔧 Customize
- Edit `final_rubric.csv` to change grading criteria
- Adjust OpenAI settings in the configuration

## 🛑 Stop the System
Press `Ctrl+C` in the terminal where the system is running.

## 🎉 Ready to Grade!
The system is fully functional with a modern web interface for AI-powered grading!
