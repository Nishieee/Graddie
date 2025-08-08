#!/bin/bash

echo "🚀 Testing Graddie Web Interface..."
echo ""

# Wait a bit for server to start
sleep 3

echo "📝 Testing MCQ submission..."
curl -X POST http://localhost:8080/grade \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": "WEB001",
    "assignment": "Web UI Test - MCQ",
    "questionType": "MCQ",
    "correctAnswers": "What is Akka?\nA. Database\nB. Actor Framework\nC. Web Server\nD. Compiler\nAnswer: B\n\nWhat does tell do?\nA. Blocks\nB. Fire-and-forget\nC. Returns value\nD. Crashes\nAnswer: B",
    "submission": "What is Akka?\nA. Database\nB. Actor Framework\nC. Web Server\nD. Compiler\nAnswer: B\n\nWhat does tell do?\nA. Blocks\nB. Fire-and-forget\nC. Returns value\nD. Crashes\nAnswer: A"
  }' | jq .

echo ""
echo "✅ Web UI test completed!"
echo "🌐 Open http://localhost:8080 in your browser to test the interface manually."
echo ""
echo "Press ENTER to stop the web server..."
read