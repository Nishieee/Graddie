package com.agentic;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.marshallers.jackson.Jackson;
import com.agentic.actors.GraddieMessages;
import com.agentic.actors.GradingCoordinatorActor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WebServer extends AllDirectives {
    
    private final ActorSystem<Void> system;
    private final ObjectMapper objectMapper;

    public WebServer(ActorSystem<Void> system) {
        this.system = system;
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        System.out.println("🚀 Starting Graddie Web Server...");
        
        ActorSystem<Void> system = ActorSystem.create(
            Behaviors.empty(), 
            "GraddieWebSystem"
        );

        try {
            WebServer server = new WebServer(system);
            CompletionStage<ServerBinding> binding = server.startServer();
            
            System.out.println("✅ Graddie Web Server started successfully!");
            System.out.println("🌐 Open your browser and go to: http://localhost:8080");
            System.out.println("⏹️  Press Ctrl+C to stop the server...");
            
            Thread.currentThread().join();
            
            binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> {
                    System.out.println("🛑 Server shutting down...");
                    system.terminate();
                });
                
        } catch (InterruptedException e) {
            System.out.println("🛑 Server interrupted, shutting down...");
            system.terminate();
        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
            system.terminate();
        }
    }

    private CompletionStage<ServerBinding> startServer() {
        final Http http = Http.get(system);

        return http.newServerAt("localhost", 8080)
                .bind(createRoute())
                .thenApply(binding -> {
                    System.out.println("🌐 Server online at http://localhost:8080/");
                    return binding;
                });
    }

    private Route createRoute() {
        return concat(
            pathSingleSlash(() -> {
                String html = getHTMLInterface();
                System.out.println("📄 Serving HTML interface...");
                return complete(HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(ContentTypes.TEXT_HTML_UTF8, html)
                );
            }),
            
            path("grade", () ->
                post(() ->
                    entity(Jackson.unmarshaller(objectMapper, SubmissionRequest.class), request -> {
                        System.out.println("📝 Received grading request for student: " + request.studentId);
                        
                        CompletableFuture<GraddieMessages.GradingComplete> gradingFuture = 
                            processGradingRequest(request);
                        
                        return onSuccess(gradingFuture, result -> {
                            try {
                                WebResponse webResponse = new WebResponse(
                                    result.getStudentId(),
                                    result.getAssignmentName(),
                                    request.questionType,
                                    result.getTotalScore(),
                                    result.getMaxPossibleScore(),
                                    result.getTotalScore(),
                                    result.getGrade(),
                                    result.getOverallFeedback(),
                                    result.getDetailedMCQFeedback(),
                                    result.getDetailedFeedback(),
                                    java.time.LocalDateTime.now().toString()
                                );
                                
                                String jsonResponse = objectMapper.writeValueAsString(webResponse);
                                System.out.println("✅ Grading completed for " + request.studentId);
                                return complete(HttpResponse.create()
                                    .withStatus(StatusCodes.OK)
                                    .withEntity(ContentTypes.APPLICATION_JSON, jsonResponse)
                                );
                            } catch (Exception e) {
                                System.err.println("❌ Error serializing response: " + e.getMessage());
                                return complete(HttpResponse.create()
                                    .withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                                    .withEntity(ContentTypes.APPLICATION_JSON, 
                                        "{\"error\":\"Failed to process response\"}")
                                );
                            }
                        });
                    })
                )
            ),
            
            pathPrefix("", () ->
                complete(HttpResponse.create()
                    .withStatus(StatusCodes.NOT_FOUND)
                    .withEntity(ContentTypes.TEXT_PLAIN_UTF8, "Not Found"))
            )
        );
    }

    private String getHTMLInterface() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Graddie - AI Grading System</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: Arial, sans-serif;
                        background: #ffffff;
                        color: #000000;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .container {
                        max-width: 800px;
                        margin: 0 auto;
                        background: #ffffff;
                        border: 1px solid #000000;
                        padding: 20px;
                    }
                    .header {
                        background: #000000;
                        color: #ffffff;
                        padding: 20px;
                        text-align: center;
                        margin: -20px -20px 20px -20px;
                    }
                    .header h1 { font-size: 2em; margin-bottom: 10px; }
                    .header p { font-size: 1em; }
                    .form-container { padding: 20px; }
                    .form-group { margin-bottom: 20px; }
                    label {
                        display: block;
                        margin-bottom: 5px;
                        font-weight: bold;
                        color: #000000;
                    }
                    input, select, textarea {
                        width: 100%;
                        padding: 10px;
                        border: 1px solid #000000;
                        font-size: 16px;
                        background: #ffffff;
                        color: #000000;
                    }
                    input:focus, select:focus, textarea:focus {
                        outline: none;
                        border: 2px solid #000000;
                    }
                    textarea {
                        resize: vertical;
                        min-height: 100px;
                    }
                    .submit-btn {
                        background: #000000;
                        color: #ffffff;
                        padding: 15px 30px;
                        border: 1px solid #000000;
                        font-size: 16px;
                        font-weight: bold;
                        cursor: pointer;
                        width: 100%;
                    }
                    .submit-btn:hover { background: #333333; }
                    .submit-btn:disabled { background: #cccccc; cursor: not-allowed; }
                    .result-container {
                        margin-top: 30px;
                        padding: 20px;
                        background: #ffffff;
                        border: 1px solid #000000;
                        display: none;
                    }
                    .result-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 20px;
                        padding-bottom: 15px;
                        border-bottom: 1px solid #000000;
                    }
                    .score {
                        font-size: 2em;
                        font-weight: bold;
                        color: #000000;
                    }
                    .grade {
                        font-size: 1.5em;
                        font-weight: bold;
                        padding: 8px 16px;
                        border: 1px solid #000000;
                        color: #000000;
                        background: #ffffff;
                    }
                    .feedback-section { margin-top: 20px; }
                    .feedback-section h3 { color: #000000; margin-bottom: 10px; }
                    .feedback-text {
                        background: #ffffff;
                        padding: 15px;
                        border: 1px solid #000000;
                        line-height: 1.6;
                        color: #000000;
                    }
                    .loading {
                        text-align: center;
                        padding: 20px;
                        color: #000000;
                        font-weight: bold;
                    }
                    .error {
                        background: #ffffff;
                        color: #000000;
                        padding: 15px;
                        border: 1px solid #000000;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Graddie</h1>
                        <p>AI-Powered Grading System</p>
                    </div>
                    
                    <div class="form-container">
                        <form id="gradingForm">
                            <div class="form-group">
                                <label for="studentId">Student ID:</label>
                                <input type="text" id="studentId" name="studentId" required>
                            </div>
                            
                            <div class="form-group">
                                <label for="assignment">Assignment Name:</label>
                                <select id="assignment" name="assignment" required>
                                    <option value="">Select an assignment</option>
                                    <option value="Assignment 1">Assignment 1</option>
                                    <option value="Assignment 2">Assignment 2</option>
                                    <option value="Assignment 3">Assignment 3</option>
                                    <option value="Assignment 4">Assignment 4</option>
                                    <option value="Assignment 5">Assignment 5</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label for="questionType">Question Type:</label>
                                <select id="questionType" name="questionType" required>
                                    <option value="">Select question type...</option>
                                    <option value="MCQ">MCQ (Multiple Choice Questions)</option>
                                    <option value="SHORT_ANSWER">Short Answer</option>
                                    <option value="ESSAY">Essay</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label for="correctAnswers">Correct Answers (for MCQ):</label>
                                <textarea id="correctAnswers" name="correctAnswers" 
                                    placeholder="For MCQ: Enter questions with options and correct answers&#10;For other types: Leave empty"></textarea>
                            </div>
                            
                            <div class="form-group">
                                <label for="submission">Student Submission:</label>
                                <textarea id="submission" name="submission" required 
                                    placeholder="Enter the student's submission here..."></textarea>
                            </div>
                            
                            <button type="submit" class="submit-btn" id="submitBtn">
                                Grade Submission
                            </button>
                        </form>
                        
                        <div id="loading" class="loading" style="display: none;">
                            Processing submission with AI... Please wait...
                        </div>
                        
                        <div id="result" class="result-container">
                            <div class="result-header">
                                <div>
                                    <h2 id="resultTitle">Grading Results</h2>
                                    <p id="resultSubtitle">Student: <span id="resultStudent"></span> | Assignment: <span id="resultAssignment"></span></p>
                                </div>
                                <div>
                                    <div class="score" id="resultScore">0/100</div>
                                    <div class="grade" id="resultGrade">F</div>
                                </div>
                            </div>
                            
                            <div class="feedback-section">
                                <h3>Overall Feedback</h3>
                                <div class="feedback-text" id="overallFeedback">
                                    No feedback available.
                                </div>
                            </div>
                            
                            <div class="feedback-section" id="mcqFeedbackSection" style="display: none;">
                                <h3>Detailed MCQ Feedback</h3>
                                <div class="feedback-text" id="mcqFeedback">
                                    No MCQ feedback available.
                                </div>
                            </div>
                            
                            <div class="feedback-section" id="detailedFeedbackSection" style="display: none;">
                                <h3>Detailed Short Answer Feedback</h3>
                                <div class="feedback-text" id="detailedFeedback">
                                    No detailed feedback available.
                                </div>
                            </div>
                            
                            <div style="text-align: center; margin-top: 20px; padding-top: 15px; border-top: 1px solid #000000;">
                                <button style="background: #4CAF50; color: white; padding: 12px 25px; border: none; border-radius: 5px; font-size: 16px; font-weight: bold; cursor: pointer;" 
                                        onclick="downloadResults()">
                                    📄 Download Grading Report
                                </button>
                            </div>
                        </div>
                        
                        <div id="error" class="error" style="display: none;"></div>
                    </div>
                </div>
                
                <script>
                    document.getElementById('gradingForm').addEventListener('submit', async function(e) {
                        e.preventDefault();
                        
                        const submitBtn = document.getElementById('submitBtn');
                        const loading = document.getElementById('loading');
                        const result = document.getElementById('result');
                        const error = document.getElementById('error');
                        
                        submitBtn.disabled = true;
                        loading.style.display = 'block';
                        result.style.display = 'none';
                        error.style.display = 'none';
                        
                        const formData = {
                            studentId: document.getElementById('studentId').value,
                            assignment: document.getElementById('assignment').value,
                            questionType: document.getElementById('questionType').value,
                            correctAnswers: document.getElementById('correctAnswers').value,
                            submission: document.getElementById('submission').value
                        };
                        
                        try {
                            const response = await fetch('/grade', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json',
                                },
                                body: JSON.stringify(formData)
                            });
                            
                            if (!response.ok) {
                                throw new Error(`HTTP error! status: ${response.status}`);
                            }
                            
                            const data = await response.json();
                            
                            // Store data for download functionality
                            gradingData = data;
                            
                            document.getElementById('resultStudent').textContent = data.studentId;
                            document.getElementById('resultAssignment').textContent = data.assignment;
                            document.getElementById('resultScore').textContent = data.totalScore + '/' + data.maxScore;
                            document.getElementById('resultGrade').textContent = data.grade;
                            document.getElementById('resultGrade').className = 'grade ' + data.grade;
                            document.getElementById('overallFeedback').textContent = data.overallFeedback || 'No feedback available.';
                            
                            const mcqSection = document.getElementById('mcqFeedbackSection');
                            const mcqFeedback = document.getElementById('mcqFeedback');
                            if (data.mcqFeedback && data.mcqFeedback.trim() !== '') {
                                mcqFeedback.textContent = data.mcqFeedback;
                                mcqSection.style.display = 'block';
                            } else {
                                mcqSection.style.display = 'none';
                            }
                            
                            const detailedSection = document.getElementById('detailedFeedbackSection');
                            const detailedFeedback = document.getElementById('detailedFeedback');
                            if (data.detailedFeedback && data.detailedFeedback.trim() !== '') {
                                detailedFeedback.textContent = data.detailedFeedback;
                                detailedSection.style.display = 'block';
                            } else {
                                detailedSection.style.display = 'none';
                            }
                            
                            result.style.display = 'block';
                            
                        } catch (err) {
                            console.error('Error:', err);
                            error.textContent = 'Error: ' + err.message;
                            error.style.display = 'block';
                        } finally {
                            loading.style.display = 'none';
                            submitBtn.disabled = false;
                        }
                    });
                    
                    // Global variable for download functionality
                    let gradingData = null;
                    
                    function downloadResults() {
                        if (!gradingData) {
                            alert('No grading results available to download.');
                            return;
                        }
                        
                        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
                        const filename = `grading_report_${gradingData.studentId}_${timestamp}.txt`;
                        
                        // Get form data
                        const formData = {
                            studentId: document.getElementById('studentId').value,
                            assignment: document.getElementById('assignment').value,
                            submission: document.getElementById('submission').value
                        };
                        
                        // Create report content
                        let content = '';
                        content += '================================\\n';
                        content += '       GRADDIE GRADING REPORT\\n';
                        content += '================================\\n\\n';
                        content += `Student ID: ${gradingData.studentId}\\n`;
                        content += `Assignment: ${gradingData.assignment}\\n`;
                        content += `Grade: ${gradingData.grade}\\n`;
                        content += `Score: ${gradingData.totalScore}/${gradingData.maxScore}\\n`;
                        content += `Graded At: ${new Date().toLocaleString()}\\n\\n`;
                        
                        content += '================================\\n';
                        content += '       STUDENT SUBMISSION\\n';
                        content += '================================\\n\\n';
                        content += formData.submission + '\\n\\n';
                        
                        if (gradingData.overallFeedback) {
                            content += '================================\\n';
                            content += '       OVERALL FEEDBACK\\n';
                            content += '================================\\n\\n';
                            content += gradingData.overallFeedback + '\\n\\n';
                        }
                        
                        if (gradingData.mcqFeedback) {
                            content += '================================\\n';
                            content += '       MCQ BREAKDOWN\\n';
                            content += '================================\\n\\n';
                            content += gradingData.mcqFeedback + '\\n\\n';
                        }
                        
                        content += '================================\\n';
                        content += '   Generated by Graddie AI\\n';
                        content += '================================\\n';
                        
                        // Download the file
                        const blob = new Blob([content], { type: 'text/plain' });
                        const url = window.URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = filename;
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        window.URL.revokeObjectURL(url);
                    }
                </script>
            </body>
            </html>
            """;
    }

    private CompletableFuture<GraddieMessages.GradingComplete> processGradingRequest(SubmissionRequest request) {
        CompletableFuture<GraddieMessages.GradingComplete> resultFuture = new CompletableFuture<>();
        
        Behavior<GraddieMessages.Message> resultListener = Behaviors.receive((context, message) -> {
            if (message instanceof GraddieMessages.GradingComplete) {
                GraddieMessages.GradingComplete result = (GraddieMessages.GradingComplete) message;
                System.out.println("✅ Grading completed for student: " + result.getStudentId() + 
                    " with score: " + result.getTotalScore() + "/" + result.getMaxPossibleScore());
                resultFuture.complete(result);
                return Behaviors.stopped();
            } else if (message instanceof GraddieMessages.GradingFailed) {
                GraddieMessages.GradingFailed failure = (GraddieMessages.GradingFailed) message;
                System.err.println("❌ Grading failed for student: " + request.studentId + " - " + failure.getError());
                resultFuture.completeExceptionally(new RuntimeException(failure.getError()));
                return Behaviors.stopped();
            }
            return Behaviors.same();
        });
        
        ActorRef<GraddieMessages.Message> listener = system.systemActorOf(resultListener, "result-listener-" + System.currentTimeMillis(), Props.empty());
        
        ActorRef<GraddieMessages.Message> coordinator = system.systemActorOf(
            GradingCoordinatorActor.create(listener), "coordinator-" + System.currentTimeMillis(), Props.empty());
        
        GraddieMessages.QuestionType questionType;
        try {
            questionType = GraddieMessages.QuestionType.valueOf(request.questionType);
        } catch (IllegalArgumentException e) {
            resultFuture.completeExceptionally(new IllegalArgumentException("Invalid question type: " + request.questionType));
            return resultFuture;
        }
        
        GraddieMessages.StartGrading startGrading = new GraddieMessages.StartGrading(
            request.studentId,
            request.assignment,
            request.submission,
            questionType,
            request.correctAnswers
        );
        
        coordinator.tell(startGrading);
        
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (!resultFuture.isDone()) {
                resultFuture.completeExceptionally(new RuntimeException("Grading timeout"));
            }
        }, 3, java.util.concurrent.TimeUnit.MINUTES);
        
        return resultFuture;
    }

    public static class SubmissionRequest {
        public String studentId;
        public String assignment;
        public String questionType;
        public String correctAnswers;
        public String submission;
        
        public SubmissionRequest() {}
    }
    
    public static class WebResponse {
        public String studentId;
        public String assignment;
        public String questionType;
        public int totalScore;
        public int maxScore;
        public int percentage;
        public String grade;
        public String overallFeedback;
        public String mcqFeedback;
        public String detailedFeedback;
        public String gradedAt;
        
        public WebResponse(String studentId, String assignment, String questionType, 
                          int totalScore, int maxScore, int percentage, String grade,
                          String overallFeedback, String mcqFeedback, String detailedFeedback, String gradedAt) {
            this.studentId = studentId;
            this.assignment = assignment;
            this.questionType = questionType;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.percentage = percentage;
            this.grade = grade;
            this.overallFeedback = overallFeedback;
            this.mcqFeedback = mcqFeedback;
            this.detailedFeedback = detailedFeedback;
            this.gradedAt = gradedAt;
        }
        
        public WebResponse() {}
    }
} 