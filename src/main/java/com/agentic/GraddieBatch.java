package com.agentic;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import com.agentic.actors.GraddieMessages;
import com.agentic.actors.GradingCoordinatorActor;
import com.agentic.actors.SubmissionReceiverActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Scanner;

/**
 * Batch processing interface for Graddie - takes input from text file and outputs JSON
 */
public class GraddieBatch {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GraddieBatch <input_file.txt> [output_file.json]");
            System.out.println("Input file format (same as CLI):");
            System.out.println("Line 1: Student ID");
            System.out.println("Line 2: Assignment Name");
            System.out.println("Line 3: Question Type (1=MCQ, 2=SHORT_ANSWER, 3=ESSAY)");
            System.out.println("Line 4+: Correct answers (for MCQ/Short Answer, until 'END')");
            System.out.println("Then: Student submission (until 'END' or end of file)");
            return;
        }
        
        String inputFile = args[0];
        String outputFile = args.length > 1 ? args[1] : "grading_results.json";
        
        try {
            // Read and parse input file
            String content = Files.readString(Paths.get(inputFile));
            InputData input = parseInputFile(content);
            
            // Process the submission
            ObjectNode result = processSubmission(input);
            
            // Write to output file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), result);
            
            System.out.println("✅ Grading completed!");
            System.out.println("📄 Input: " + inputFile);
            System.out.println("📄 Output: " + outputFile);
            System.out.println("📊 Score: " + result.get("totalScore") + "/" + result.get("maxScore") + " (" + result.get("grade") + ")");
            
        } catch (Exception e) {
            System.err.println("❌ Error processing file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static InputData parseInputFile(String content) {
        String[] lines = content.split("\n");
        
        // Expected format based on CLI:
        // Line 0: Student ID
        // Line 1: Assignment Name  
        // Line 2: Question Type (1=MCQ, 2=SHORT_ANSWER, 3=ESSAY)
        // Line 3+: Correct answers (if applicable, until "END")
        // Then: Student submission (until "END" or end of file)
        
        if (lines.length < 3) {
            throw new IllegalArgumentException("Input file must have at least 3 lines: Student ID, Assignment, Question Type");
        }
        
        String studentId = lines[0].trim();
        String assignment = lines[1].trim();
        String questionTypeNum = lines[2].trim();
        
        GraddieMessages.QuestionType questionType;
        switch (questionTypeNum) {
            case "1":
                questionType = GraddieMessages.QuestionType.MCQ;
                break;
            case "2":
                questionType = GraddieMessages.QuestionType.SHORT_ANSWER;
                break;
            case "3":
                questionType = GraddieMessages.QuestionType.ESSAY;
                break;
            default:
                throw new IllegalArgumentException("Invalid question type: " + questionTypeNum + ". Use 1=MCQ, 2=SHORT_ANSWER, 3=ESSAY");
        }
        
        String correctAnswers = null;
        StringBuilder submission = new StringBuilder();
        int currentLine = 3;
        
        // Parse correct answers if not essay
        if (questionType != GraddieMessages.QuestionType.ESSAY) {
            StringBuilder correctAnswersBuilder = new StringBuilder();
            while (currentLine < lines.length) {
                String line = lines[currentLine].trim();
                if (line.equals("END")) {
                    currentLine++;
                    break;
                }
                correctAnswersBuilder.append(line).append("\n");
                currentLine++;
            }
            correctAnswers = correctAnswersBuilder.toString().trim();
        }
        
        // Parse submission
        while (currentLine < lines.length) {
            String line = lines[currentLine].trim();
            if (line.equals("END")) {
                break;
            }
            submission.append(line).append("\n");
            currentLine++;
        }
        
        return new InputData(studentId, assignment, questionType, correctAnswers, submission.toString().trim());
    }
    
    private static ObjectNode processSubmission(InputData input) throws Exception {
        CompletableFuture<ObjectNode> resultFuture = new CompletableFuture<>();
        
        ActorSystem<Void> system = ActorSystem.create(
            Behaviors.setup(context -> {
                // Create result listener
                ActorRef<GraddieMessages.Message> resultListener = context.spawn(
                    Behaviors.receive(GraddieMessages.Message.class)
                        .onMessage(GraddieMessages.GradingComplete.class, msg -> {
                            try {
                                ObjectNode result = createJsonResult(msg, input);
                                resultFuture.complete(result);
                            } catch (Exception e) {
                                resultFuture.completeExceptionally(e);
                            }
                            return Behaviors.same();
                        })
                        .onMessage(GraddieMessages.GradingFailed.class, msg -> {
                            resultFuture.completeExceptionally(new RuntimeException("Grading failed: " + msg.getError()));
                            return Behaviors.same();
                        })
                        .build(),
                    "result-listener"
                );
                
                // Create coordinator and submission receiver
                ActorRef<GraddieMessages.Message> coordinator = context.spawn(
                    GradingCoordinatorActor.create(resultListener), "coordinator"
                );
                
                ActorRef<GraddieMessages.Message> submissionReceiver = context.spawn(
                    SubmissionReceiverActor.create(coordinator), "submission-receiver"
                );
                
                // Send the submission
                submissionReceiver.tell(new GraddieMessages.StudentSubmission(
                    input.studentId,
                    input.assignment,
                    input.submission,
                    input.questionType,
                    input.correctAnswers
                ));
                
                return Behaviors.empty();
            }),
            "GraddieBatch"
        );
        
        try {
            // Wait for result with timeout
            ObjectNode result = resultFuture.get(180, TimeUnit.SECONDS);
            return result;
        } finally {
            system.terminate();
        }
    }
    
    private static ObjectNode createJsonResult(GraddieMessages.GradingComplete msg, InputData input) {
        ObjectNode result = objectMapper.createObjectNode();
        
        // Basic info
        result.put("studentId", msg.getStudentId());
        result.put("assignment", msg.getAssignmentName());
        result.put("questionType", input.questionType.toString());
        result.put("totalScore", msg.getTotalScore());
        result.put("maxScore", msg.getMaxPossibleScore());
        result.put("percentage", Math.round((double) msg.getTotalScore() / msg.getMaxPossibleScore() * 100.0));
        result.put("grade", msg.getGrade());
        
        // Feedback
        if (msg.getOverallFeedback() != null && !msg.getOverallFeedback().isEmpty()) {
            result.put("overallFeedback", msg.getOverallFeedback());
        }
        
        // MCQ specific details
        if (msg.getDetailedMCQFeedback() != null && !msg.getDetailedMCQFeedback().isEmpty()) {
            result.put("mcqFeedback", msg.getDetailedMCQFeedback());
        }
        
        // Timestamp
        result.put("gradedAt", java.time.LocalDateTime.now().toString());
        
        return result;
    }
    
    private static class InputData {
        final String studentId;
        final String assignment;
        final GraddieMessages.QuestionType questionType;
        final String correctAnswers;
        final String submission;
        
        InputData(String studentId, String assignment, GraddieMessages.QuestionType questionType, 
                 String correctAnswers, String submission) {
            this.studentId = studentId;
            this.assignment = assignment;
            this.questionType = questionType;
            this.correctAnswers = correctAnswers;
            this.submission = submission;
        }
    }
}