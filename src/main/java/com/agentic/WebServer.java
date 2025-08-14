package com.agentic;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
import com.agentic.actors.GradingWorkerActor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WebServer extends AllDirectives {
    
    private final ActorSystem<Void> system;
    private final ObjectMapper objectMapper;

    public WebServer(ActorSystem<Void> system) {
        this.system = system;
        this.objectMapper = new ObjectMapper();
        // Also spawn a few local workers so requests are processed even if cluster workers are unavailable
        try {
            system.systemActorOf(Behaviors.setup(ctx -> {
                int workers = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
                for (int i = 0; i < workers; i++) {
                    ctx.spawn(GradingWorkerActor.create(), "web-grading-worker-" + i);
                }
                return Behaviors.empty();
            }), "web-worker-bootstrap", Props.empty());
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        System.out.println("🚀 Starting Graddie Web Server...");
        
        // Join the same cluster name as other nodes, using web.conf for port/host
        Config config = ConfigFactory.parseResources("web.conf").withFallback(ConfigFactory.load());
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "GraddieCluster", config);

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
                    .addHeader(akka.http.javadsl.model.headers.RawHeader.create("Cache-Control", "no-store, max-age=0"))
                    .withEntity(ContentTypes.TEXT_HTML_UTF8, html)
                );
            }),
            
            path("assignments", () ->
                get(() -> {
                    try {
                        String json = getAssignmentsJson();
                        return complete(HttpResponse.create()
                            .withStatus(StatusCodes.OK)
                            .addHeader(akka.http.javadsl.model.headers.RawHeader.create("Cache-Control", "no-store, max-age=0"))
                            .withEntity(ContentTypes.APPLICATION_JSON, json)
                        );
                    } catch (Exception e) {
                        return complete(HttpResponse.create()
                            .withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                            .withEntity(ContentTypes.APPLICATION_JSON, "{\"error\":\"Failed to load assignments\"}")
                        );
                    }
                })
            ),
            
            path("grade", () ->
                post(() ->
                    entity(Jackson.unmarshaller(objectMapper, SubmissionRequest.class), request -> {
                        System.out.println("📝 Received grading request for student: " + request.studentId);
                        
                        CompletableFuture<GraddieMessages.GradingComplete> gradingFuture = 
                            processGradingRequest(request).orTimeout(170, java.util.concurrent.TimeUnit.SECONDS);
                        
                        return onComplete(gradingFuture, tryResult -> {
                            if (tryResult.isSuccess()) {
                                try {
                                    GraddieMessages.GradingComplete result = tryResult.get();
                                    int percentage = result.getMaxPossibleScore() > 0 ?
                                        (int) Math.round(result.getTotalScore() * 100.0 / result.getMaxPossibleScore()) : 0;
                                    WebResponse webResponse = new WebResponse(
                                        result.getStudentId(),
                                        result.getAssignmentName(),
                                         deriveAssignmentConfig(request.assignment).type,
                                        result.getTotalScore(),
                                        result.getMaxPossibleScore(),
                                        percentage,
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
                            } else {
                                Throwable ex = tryResult.failed().get();
                                System.err.println("❌ Grading failed: " + ex.getMessage());
                                String errJson = String.format("{\"error\":\"%s\"}", ex.getMessage().replace("\"", "'"));
                                return complete(HttpResponse.create()
                                    .withStatus(StatusCodes.BAD_REQUEST)
                                    .withEntity(ContentTypes.APPLICATION_JSON, errJson)
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

    private String getAssignmentsJson() throws Exception {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        java.util.Map<String, Object> a1 = new java.util.HashMap<>();
        a1.put("type", "MCQ");
        a1.put("detailsText", "Assignment 1: Multiple Choice (MCQ)\nTopic: Basic Math\n\n1. What is 5 + 3?\na) 6\nb) 8\nc) 10\nd) 9\n\n2. Which number is even?\na) 7\nb) 5\nc) 4\nd) 9\n\n3. What is 10 - 6?\na) 2\nb) 3\nc) 4\nd) 5");
        a1.put("correct", "Question 1: What is 5 + 3?\nA. 6\nB. 8\nC. 10\nD. 9\nAnswer: B\n\nQuestion 2: Which number is even?\nA. 7\nB. 5\nC. 4\nD. 9\nAnswer: C\n\nQuestion 3: What is 10 - 6?\nA. 2\nB. 3\nC. 4\nD. 5\nAnswer: C");
        a1.put("template", "Question 1: What is 5 + 3?\nA. 6\nB. 8\nC. 10\nD. 9\nAnswer: \n\nQuestion 2: Which number is even?\nA. 7\nB. 5\nC. 4\nD. 9\nAnswer: \n\nQuestion 3: What is 10 - 6?\nA. 2\nB. 3\nC. 4\nD. 5\nAnswer: ");

        java.util.Map<String, Object> a2 = new java.util.HashMap<>();
        a2.put("type", "SHORT_ANSWER");
        a2.put("detailsText", "Assignment 2: Short Answer\nTopic: Everyday English\n\n1. Write one synonym for \"happy\".\n2. What is the opposite of \"hot\"?\n3. Complete the sentence: \"I like to eat ____ in the morning.\"");
        a2.put("correct", "Guidance for evaluation:\n1) Synonyms for \"happy\": joyful, glad, cheerful, pleased, content.\n2) Opposite of \"hot\": cold.\n3) Acceptable completions include common breakfast foods, e.g., eggs, bread, cereal, fruit, toast, pancakes.");
        a2.put("template", "1. Write one synonym for \"happy\".\nAnswer: \n\n2. What is the opposite of \"hot\"?\nAnswer: \n\n3. Complete the sentence: \"I like to eat ____ in the morning.\"\nAnswer: ");

        java.util.Map<String, Object> a3 = new java.util.HashMap<>();
        a3.put("type", "ESSAY");
        a3.put("detailsText", "Assignment 3: Essay\nPrompt: Does technology bring people closer together, or does it push them further apart?\n\nWrite a well-structured essay addressing the prompt. Use specific examples and support your position.");
        a3.put("correct", "");
        a3.put("template", "Essay Prompt:\nDoes technology bring people closer together, or does it push them further apart?\n\nYour Response:\n");

        java.util.Map<String, Object> assignments = new java.util.HashMap<>();
        assignments.put("Assignment 1", a1);
        assignments.put("Assignment 2", a2);
        assignments.put("Assignment 3", a3);
        data.put("assignments", assignments);
        return objectMapper.writeValueAsString(data);
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
                    .assignment-preview {
                        background: #f8f9fa !important;
                        border: 1px solid #ddd !important;
                        padding: 15px !important;
                        margin: 15px 0 !important;
                        border-radius: 5px !important;
                    }
                    .assignment-preview h3 {
                        margin-bottom: 10px !important;
                        color: #333 !important;
                        font-size: 16px !important;
                    }
                    .assignment-preview pre {
                        white-space: pre-wrap !important;
                        margin: 0 !important;
                        background: white !important;
                        padding: 10px !important;
                        border-radius: 3px !important;
                        font-family: inherit !important;
                        line-height: 1.4 !important;
                        border: 1px solid #eee !important;
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
                                <select id="studentId" name="studentId" required>
                                    <option value="">Select a student</option>
                                    <option value="STU001">STU001</option>
                                    <option value="STU002">STU002</option>
                                </select>
                            </div>
                            
                            <div class="form-group">
                                <label for="assignment">Assignment Name:</label>
                                <select id="assignment" name="assignment" required onchange="updateAssignmentPreview()">
                                    <option value="">Select an assignment</option>
                                    <option value="Assignment 1">Assignment 1: Multiple Choice (MCQ) Topic: Basic Math</option>
                                    <option value="Assignment 2">Assignment 2: Short Answer Topic: Everyday English</option>
                                     <option value="Assignment 3">Assignment 3: Essay Prompt on Technology and Society</option>
                                </select>
                            </div>

                            <div class="assignment-preview" id="assignmentDetails" style="display: block; background: #f8f9fa; border: 1px solid #ddd; padding: 15px; margin: 15px 0; border-radius: 5px;">
                                <h3 style="margin-bottom: 10px; color: #333;">📝 Assignment Preview</h3>
                                <pre id="assignmentDetailsPre" style="white-space: pre-wrap; margin: 0; background: white; padding: 10px; border-radius: 3px; font-family: inherit; line-height: 1.4;">Select an assignment to see its content here.</pre>
                            </div>
                            
                            <!-- Question Type removed: derived from assignment selection -->
                            
                            <div class="form-group" id="correctAnswersGroup" style="display: none;">
                                <label for="correctAnswers">Reference (Predefined)</label>
                                <textarea id="correctAnswers" name="correctAnswers" readonly
                                    placeholder="Predefined reference for this assignment will appear here..."></textarea>
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
                            correctAnswers: document.getElementById('correctAnswers').value,
                            submission: document.getElementById('submission').value
                        };
                        
                        try {
                            const response = await fetch('/grade', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json',
                                    'Accept': 'application/json'
                                },
                                body: JSON.stringify(formData)
                            });

                            const raw = await response.text();
                            let data;
                            try {
                                data = JSON.parse(raw);
                            } catch (e) {
                                throw new Error(raw && raw.trim() ? raw.trim() : `HTTP ${response.status}`);
                            }

                            if (!response.ok || data.error) {
                                throw new Error(data.error || `HTTP ${response.status}`);
                            }

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

                    // Predefined assignments mapping
                    const predefinedAssignments = {
                        'Assignment 1': {
                            type: 'MCQ',
                            detailsText: 'Assignment 1: Multiple Choice (MCQ)\\nTopic: Basic Math\\n\\n1. What is 5 + 3?\\na) 6\\nb) 8\\nc) 10\\nd) 9\\n\\n2. Which number is even?\\na) 7\\nb) 5\\nc) 4\\nd) 9\\n\\n3. What is 10 - 6?\\na) 2\\nb) 3\\nc) 4\\nd) 5',
                            correct: 'Question 1: What is 5 + 3?\\nA. 6\\nB. 8\\nC. 10\\nD. 9\\nAnswer: B\\n\\nQuestion 2: Which number is even?\\nA. 7\\nB. 5\\nC. 4\\nD. 9\\nAnswer: C\\n\\nQuestion 3: What is 10 - 6?\\nA. 2\\nB. 3\\nC. 4\\nD. 5\\nAnswer: C',
                            template: 'Question 1: What is 5 + 3?\\nA. 6\\nB. 8\\nC. 10\\nD. 9\\nAnswer: \\n\\nQuestion 2: Which number is even?\\nA. 7\\nB. 5\\nC. 4\\nD. 9\\nAnswer: \\n\\nQuestion 3: What is 10 - 6?\\nA. 2\\nB. 3\\nC. 4\\nD. 5\\nAnswer: '
                        },
                        'Assignment 2': {
                            type: 'SHORT_ANSWER',
                            detailsText: 'Assignment 2: Short Answer\\nTopic: Everyday English\\n\\n1. Write one synonym for "happy".\\n2. What is the opposite of "hot"?\\n3. Complete the sentence: "I like to eat ____ in the morning."',
                            correct: 'Guidance for evaluation:\\n1) Synonyms for "happy": joyful, glad, cheerful, pleased, content.\\n2) Opposite of "hot": cold.\\n3) Acceptable completions include common breakfast foods, e.g., eggs, bread, cereal, fruit, toast, pancakes.',
                            template: '1. Write one synonym for "happy".\\nAnswer: \\n\\n2. What is the opposite of "hot"?\\nAnswer: \\n\\n3. Complete the sentence: "I like to eat ____ in the morning."\\nAnswer: '
                        },
                        'Assignment 3': {
                            type: 'ESSAY',
                            detailsText: 'Assignment 3: Essay\\nPrompt: Does technology bring people closer together, or does it push them further apart?\\n\\nWrite a well-structured essay addressing the prompt. Use specific examples and support your position.',
                            correct: '',
                            template: 'Essay Prompt:\\nDoes technology bring people closer together, or does it push them further apart?\\n\\nYour Response:\\n'
                        }
                    };

                    // Update function used by onchange and on load
                    function updateAssignmentPreview() {
                        const assignmentSelect = document.getElementById('assignment');
                        const assignment = assignmentSelect.value;
                        const detailsDiv = document.getElementById('assignmentDetails');
                        const detailsPre = document.getElementById('assignmentDetailsPre');
                        const correctAnswersGroup = document.getElementById('correctAnswersGroup');
                        const correctAnswers = document.getElementById('correctAnswers');
                        const submission = document.getElementById('submission');

                        if (predefinedAssignments[assignment]) {
                            const cfg = predefinedAssignments[assignment];
                            // Show assignment details
                            detailsPre.textContent = cfg.detailsText;
                            detailsDiv.style.display = 'block';
                            // Pre-fill references
                            correctAnswers.value = cfg.correct;
                            // Show reference only for MCQ and SHORT_ANSWER
                            correctAnswersGroup.style.display = (cfg.type === 'MCQ' || cfg.type === 'SHORT_ANSWER') ? 'block' : 'none';
                            correctAnswers.required = (cfg.type === 'MCQ');
                            // Pre-fill submission template for the student to answer
                            submission.value = cfg.template;
                        } else {
                            detailsPre.textContent = 'Select an assignment to see its content here.';
                            detailsDiv.style.display = 'block';
                            correctAnswers.value = '';
                            correctAnswersGroup.style.display = 'none';
                            correctAnswers.required = false;
                            submission.value = '';
                        }
                    }

                    // Also bind via addEventListener to be extra safe
                    document.getElementById('assignment').addEventListener('change', updateAssignmentPreview);
                    // Expose globally for inline onchange
                    window.updateAssignmentPreview = updateAssignmentPreview;

                    // Trigger once now so current selection (if any) renders immediately
        document.addEventListener('DOMContentLoaded', function() {
            updateAssignmentPreview();
            // Ensure any browsers that fail inline onchange still update once
            const sel = document.getElementById('assignment');
            if (sel) sel.dispatchEvent(new Event('change'));
        });
                    
                    // Also trigger immediately in case DOMContentLoaded has already fired
                    updateAssignmentPreview();
                    
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
        
        // Override client-provided values with predefined assignment configuration
        AssignmentConfig cfg = deriveAssignmentConfig(request.assignment);
        if (cfg == null) {
            resultFuture.completeExceptionally(new IllegalArgumentException("Unknown assignment: " + request.assignment));
            return resultFuture;
        }

        GraddieMessages.QuestionType questionType = GraddieMessages.QuestionType.valueOf(cfg.type);
        String effectiveCorrectAnswers = cfg.correct;
        
        GraddieMessages.StartGrading startGrading = new GraddieMessages.StartGrading(
            request.studentId,
            request.assignment,
            request.submission,
            questionType,
            effectiveCorrectAnswers
        );
        
        coordinator.tell(startGrading);
        
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (!resultFuture.isDone()) {
                resultFuture.completeExceptionally(new RuntimeException("Grading timeout"));
            }
        }, 3, java.util.concurrent.TimeUnit.MINUTES);
        
        return resultFuture;
    }

    private static class AssignmentConfig {
        final String type; // MCQ | SHORT_ANSWER
        final String correct; // Reference/correct answers/guidance
        AssignmentConfig(String type, String correct) {
            this.type = type;
            this.correct = correct;
        }
    }

    private AssignmentConfig deriveAssignmentConfig(String assignment) {
        if ("Assignment 1".equals(assignment)) {
            String correct = "Question 1: What is 5 + 3?\nA. 6\nB. 8\nC. 10\nD. 9\nAnswer: B\n\n" +
                             "Question 2: Which number is even?\nA. 7\nB. 5\nC. 4\nD. 9\nAnswer: C\n\n" +
                             "Question 3: What is 10 - 6?\nA. 2\nB. 3\nC. 4\nD. 5\nAnswer: C";
            return new AssignmentConfig("MCQ", correct);
        }
        if ("Assignment 2".equals(assignment)) {
            String guidance = "Guidance for evaluation:\n" +
                              "1) Synonyms for \"happy\": joyful, glad, cheerful, pleased, content.\n" +
                              "2) Opposite of \"hot\": cold.\n" +
                              "3) Acceptable completions include common breakfast foods, e.g., eggs, bread, cereal, fruit, toast, pancakes.";
            return new AssignmentConfig("SHORT_ANSWER", guidance);
        }
        if ("Assignment 3".equals(assignment)) {
            return new AssignmentConfig("ESSAY", "");
        }
        return null;
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