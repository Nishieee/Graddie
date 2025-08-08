package com.agentic;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.agentic.actors.GradingMessages;
import com.agentic.models.GradingResult;
import com.agentic.utils.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for the Agentic Grading System
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                AGENTIC GRADING SYSTEM                      ║");
        System.out.println("║                    Version 1.0.0                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        if (args.length < 1) {
            System.out.println("Usage: java -jar agentic-grader.jar <port> [create-sample-files]");
            System.out.println("  port: Port number for the Akka cluster (e.g., 2551, 2552)");
            System.out.println("  create-sample-files: Optional flag to create sample input files");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        boolean createSampleFiles = args.length > 1 && "create-sample-files".equals(args[1]);

        // Create sample files if requested
        if (createSampleFiles) {
            createSampleFiles();
        }

        // Start the grading system
        startGradingSystem(port);
    }

    private static void createSampleFiles() {
        try {
            System.out.println("📝 Creating sample input files...");
            
            // Create sample rubric CSV
            CsvUtils.createSampleRubricCsv("src/main/resources/final_rubric.csv");
            
            // Create sample submission
            String sampleSubmission = """
                The Impact of Artificial Intelligence on Modern Education
                
                Introduction
                Artificial Intelligence (AI) has emerged as a transformative force in modern education, 
                fundamentally changing how students learn and how educators teach. This essay explores 
                the multifaceted impact of AI on educational systems, examining both its benefits and 
                potential challenges.
                
                Content Quality and Depth
                AI technologies have revolutionized content delivery through personalized learning 
                platforms that adapt to individual student needs. These systems analyze student 
                performance data to provide customized educational experiences, ensuring that each 
                learner receives instruction tailored to their unique learning style and pace. 
                Furthermore, AI-powered tools can generate diverse learning materials, from interactive 
                simulations to adaptive quizzes, enhancing the overall quality of educational content.
                
                However, the implementation of AI in education raises important questions about 
                data privacy and the potential for algorithmic bias. Educational institutions must 
                carefully consider these ethical implications while harnessing AI's capabilities.
                
                Organization and Structure
                The essay demonstrates clear organization with a logical flow from introduction 
                to conclusion. Each paragraph builds upon the previous one, creating a coherent 
                argument about AI's role in education. The use of transitional phrases and 
                topic sentences helps guide the reader through the complex subject matter.
                
                Critical Thinking and Analysis
                The analysis goes beyond surface-level observations to explore deeper implications. 
                The author considers multiple perspectives, acknowledging both the benefits and 
                potential drawbacks of AI in education. This balanced approach demonstrates 
                sophisticated critical thinking skills and the ability to evaluate complex issues 
                from multiple angles.
                
                Conclusion
                While AI presents unprecedented opportunities for educational enhancement, its 
                successful integration requires careful consideration of ethical, practical, and 
                pedagogical factors. The future of education lies in finding the right balance 
                between technological innovation and human-centered learning principles.
                """;
            
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("src/main/resources/assignment_submission.txt"),
                sampleSubmission
            );
            
            System.out.println("✅ Sample files created successfully");
            System.out.println("   - src/main/resources/final_rubric.csv");
            System.out.println("   - src/main/resources/assignment_submission.txt");
            System.out.println();
            
        } catch (IOException e) {
            System.err.println("❌ Failed to create sample files: " + e.getMessage());
        }
    }

    private static void startGradingSystem(int port) {
        System.out.println("🚀 Starting Agentic Grading System...");
        System.out.println("📍 Port: " + port);
        System.out.println();

        // Create actor system
        Behavior<Void> rootBehavior = Behaviors.setup(context -> {
            // Create a listener actor to capture the final result
            ActorRef<GradingMessages.Message> resultListener = context.spawn(
                Behaviors.receive(GradingMessages.Message.class)
                    .onMessage(GradingMessages.GradingComplete.class, msg -> {
                        System.out.println();
                        displayResults(msg.getResult());
                        // Stop the system after displaying results
                        context.getSystem().terminate();
                        return Behaviors.stopped();
                    })
                    .onMessage(GradingMessages.GradingFailed.class, msg -> {
                        System.err.println("❌ Grading failed: " + msg.getError());
                        context.getSystem().terminate();
                        return Behaviors.stopped();
                    })
                    .build(),
                "result-listener"
            );

            // Create the master actor
            ActorRef<GradingMessages.Message> masterActor = context.spawn(
                Behaviors.empty(), 
                "master-actor"
            );

            // Start the grading process
            context.getSystem().scheduler().scheduleOnce(
                java.time.Duration.ofSeconds(2),
                () -> {
                    System.out.println("📊 Starting grading process...");
                    System.out.println("📋 Student: STUDENT001");
                    System.out.println("📝 Assignment: Essay Assignment");
                    System.out.println();
                    masterActor.tell(new GradingMessages.StartGrading("STUDENT001", "Essay Assignment"));
                },
                context.getSystem().executionContext()
            );

            return Behaviors.empty();
        });

        // Create actor system
        ActorSystem<Void> system = ActorSystem.create(rootBehavior, "AgenticGraderSystem");

        System.out.println("✅ Agentic Grader System started successfully");
        System.out.println("⏳ Processing grading request...");
        System.out.println();

        // Keep the system running
        try {
            Future<akka.Done> terminated = system.whenTerminated();
            Await.result(terminated, Duration.create(60, TimeUnit.SECONDS));
        } catch (Exception e) {
            System.err.println("❌ System terminated unexpectedly: " + e.getMessage());
        }
    }

    /**
     * Interactive mode for user input
     */
    public static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("🎯 INTERACTIVE GRADING MODE");
        System.out.println("==========================");
        System.out.println();
        
        System.out.print("Enter student ID: ");
        String studentId = scanner.nextLine();
        
        System.out.print("Enter assignment name: ");
        String assignmentName = scanner.nextLine();
        
        System.out.print("Enter submission file path (or press Enter for default): ");
        String submissionPath = scanner.nextLine();
        if (submissionPath.trim().isEmpty()) {
            submissionPath = "src/main/resources/assignment_submission.txt";
        }
        
        System.out.print("Enter rubric file path (or press Enter for default): ");
        String rubricPath = scanner.nextLine();
        if (rubricPath.trim().isEmpty()) {
            rubricPath = "src/main/resources/final_rubric.csv";
        }
        
        System.out.println();
        System.out.println("📊 Starting grading process...");
        System.out.println("📋 Student: " + studentId);
        System.out.println("📝 Assignment: " + assignmentName);
        System.out.println("📄 Submission: " + submissionPath);
        System.out.println("📋 Rubric: " + rubricPath);
        System.out.println();
        
        // Run grading
        CompletableFuture<GradingResult> future = runGrading(studentId, assignmentName);
        
        try {
            GradingResult result = future.get(30, TimeUnit.SECONDS);
            displayResults(result);
        } catch (Exception e) {
            System.err.println("❌ Grading failed: " + e.getMessage());
        }
        
        scanner.close();
    }

    /**
     * Display grading results in a user-friendly format
     */
    private static void displayResults(GradingResult result) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    GRADING RESULTS                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        System.out.println("📋 Student ID: " + result.getStudentId());
        System.out.println("📝 Assignment: " + result.getAssignmentName());
        System.out.println("📊 Total Score: " + result.getTotalScore() + "/" + result.getMaxPossibleScore());
        System.out.println("📈 Percentage: " + String.format("%.1f%%", result.getPercentage()));
        System.out.println("🎯 Grade: " + result.getGrade());
        System.out.println("📅 Graded At: " + result.getGradedAt());
        System.out.println();
        
        if (result.getOverallFeedback() != null) {
            System.out.println("💬 Overall Feedback:");
            System.out.println("   " + result.getOverallFeedback());
            System.out.println();
        }
        
        if (result.getCategoryScores() != null && !result.getCategoryScores().isEmpty()) {
            System.out.println("📊 Category Breakdown:");
            System.out.println("──────────────────────────────────────────────────────────────");
            
            for (GradingResult.CategoryScore categoryScore : result.getCategoryScores().values()) {
                System.out.println("📋 " + categoryScore.getCategory());
                System.out.println("   Score: " + categoryScore.getScore() + "/" + categoryScore.getMaxPoints());
                System.out.println("   Band: " + categoryScore.getScoreBand());
                if (categoryScore.getFeedback() != null) {
                    System.out.println("   Feedback: " + categoryScore.getFeedback());
                }
                System.out.println();
            }
        }
        
        System.out.println("📄 Results saved to: grading_results.csv");
        System.out.println();
        System.out.println("✅ Grading completed successfully!");
    }

    /**
     * Utility method to run grading without cluster (for testing)
     */
    public static CompletableFuture<GradingResult> runGrading(String studentId, String assignmentName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create a simple actor system for standalone grading
                Behavior<Void> behavior = Behaviors.setup(context -> {
                    // Create a promise to hold the result
                    CompletableFuture<GradingResult> resultPromise = new CompletableFuture<>();

                    // Create a listener actor to capture the result
                    ActorRef<GradingMessages.Message> listener = context.spawn(
                        Behaviors.receive(GradingMessages.Message.class)
                            .onMessage(GradingMessages.GradingComplete.class, msg -> {
                                resultPromise.complete(msg.getResult());
                                return Behaviors.stopped();
                            })
                            .onMessage(GradingMessages.GradingFailed.class, msg -> {
                                resultPromise.completeExceptionally(new RuntimeException(msg.getError()));
                                return Behaviors.stopped();
                            })
                            .build(),
                        "result-listener"
                    );

                    ActorRef<GradingMessages.Message> masterActor = context.spawn(
                        Behaviors.empty(), 
                        "standalone-master"
                    );

                    // Start grading
                    masterActor.tell(new GradingMessages.StartGrading(studentId, assignmentName));

                    return Behaviors.empty();
                });

                ActorSystem<Void> system = ActorSystem.create(behavior, "StandaloneGradingSystem");
                
                // Wait for completion
                try {
                    Future<akka.Done> terminated = system.whenTerminated();
                    Await.result(terminated, Duration.create(30, TimeUnit.SECONDS));
                } catch (Exception e) {
                    logger.error("Grading failed", e);
                }
                
                return null; // This is a placeholder - the actual result would be captured by the listener

            } catch (Exception e) {
                logger.error("Failed to run grading", e);
                throw new RuntimeException("Grading failed", e);
            }
        });
    }
} 