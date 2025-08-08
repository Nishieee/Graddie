# 🎓 Graddie - Distributed AI Grading System

A distributed Akka-based grading system that uses OpenAI GPT-3.5-turbo for intelligent assessment of student submissions.

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher
- OpenAI API key (optional - system works with mock AI if no key provided)

### Setup OpenAI API Key

#### Option 1: Environment Variable (Recommended)
```bash
export OPENAI_API_KEY="your-openai-api-key-here"
```

#### Option 2: Configuration File
Edit `src/main/resources/config.properties`:
```properties
openai.api.key=your-openai-api-key-here
```

### Build and Run

```bash
# Compile the project
mvn compile

# Run the grading system test
mvn exec:java -Dexec.mainClass="com.agentic.GraddieTest"
```

## 🤖 AI Integration

### GPT-3.5-turbo Usage
The system uses **GPT-3.5-turbo** (cheaper than GPT-4) for:
1. **Individual category evaluation** - Each rubric category is evaluated separately
2. **Overall feedback generation** - Comprehensive assessment after all categories are graded

### API Calls Per Submission
- **4 individual category evaluations** (Content Quality, Organization, Critical Thinking, Mechanics)
- **1 overall feedback generation**
- **Total: 5 API calls per submission**

### Cost Optimization
- Uses GPT-3.5-turbo instead of GPT-4 for cost efficiency
- Configurable token limits and temperature settings
- Mock AI fallback when API key is not available

## 🏗️ Architecture

### Distributed Actor System
- **SubmissionReceiverActor**: Receives student submissions
- **GradingCoordinatorActor**: Orchestrates the grading process
- **GradingWorkerActor**: Evaluates individual rubric categories
- **LLMActor**: Generates overall feedback
- **ResultWriterActor**: Saves results to CSV
- **LoggerActor**: Provides debugging and auditing

### Communication Patterns
- **Tell**: Coordinator sends work to workers
- **Ask**: Coordinator collects responses from workers
- **Forward**: Submission receiver forwards to coordinator

## 📊 Sample Output

```
🎯 Starting grading process:
   Student: STUDENT001
   Assignment: Essay Assignment
   Content: 1990 characters

✅ Rubric loaded successfully (4 categories)
🎯 Starting category grading for 4 categories...

📋 Grading category: Content Quality
✅ Evaluation completed for category Content Quality: 23/30

📋 Grading category: Organization  
✅ Evaluation completed for category Organization: 21/30

📋 Grading category: Critical Thinking
✅ Evaluation completed for category Critical Thinking: 18/30

📋 Grading category: Mechanics
✅ Evaluation completed for category Mechanics: 6/10

📄 Results saved to: grading_results.csv
✅ Grading completed successfully!
```

## 🔧 Configuration

### OpenAI Settings
Edit `src/main/resources/config.properties`:
```properties
# API Key
openai.api.key=your-openai-api-key-here

# Model Settings
openai.model=gpt-3.5-turbo
openai.max.tokens=1000
openai.temperature=0.0

# System Settings
grading.timeout.seconds=60
grading.submission.timeout.seconds=30
```

### Rubric Configuration
Edit `src/main/resources/final_rubric.csv` to customize grading criteria.

## 🔒 Security

- API keys are loaded from environment variables or config files
- `.gitignore` excludes sensitive files
- Mock AI fallback when API key is unavailable
- No hardcoded credentials in source code

## 📁 Project Structure

```
src/main/java/com/agentic/
├── actors/                    # Akka actors
│   ├── GradingCoordinatorActor.java
│   ├── GradingWorkerActor.java
│   ├── LLMActor.java
│   ├── SubmissionReceiverActor.java
│   └── ...
├── models/                    # Data models
├── utils/                     # Utilities
│   ├── OpenAIClient.java     # OpenAI API integration
│   ├── ApiKeyLoader.java     # API key management
│   └── CsvUtils.java         # CSV operations
└── GraddieTest.java          # Main test runner

src/main/resources/
├── config.properties          # Configuration
├── final_rubric.csv          # Grading rubric
└── assignment_submission.txt  # Sample submission
```

## 🧪 Testing

### Run with Mock AI (No API Key Required)
```bash
mvn exec:java -Dexec.mainClass="com.agentic.GraddieTest"
```

### Run with Real OpenAI API
```bash
export OPENAI_API_KEY="your-key-here"
mvn exec:java -Dexec.mainClass="com.agentic.GraddieTest"
```

## 💰 Cost Estimation

With GPT-3.5-turbo:
- **Individual evaluations**: ~$0.002 per submission
- **Overall feedback**: ~$0.001 per submission
- **Total cost**: ~$0.003 per submission

## 🎯 Features

- ✅ **Distributed processing** with Akka actors
- ✅ **Intelligent AI grading** with GPT-3.5-turbo
- ✅ **Comprehensive feedback** generation
- ✅ **CSV result export**
- ✅ **Mock AI fallback**
- ✅ **Cost-optimized** API usage
- ✅ **Secure API key management**
- ✅ **Real-time progress tracking**

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test with both mock and real API
5. Submit a pull request

## 📄 License

MIT License - see LICENSE file for details. 