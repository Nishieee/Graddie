package com.agentic;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.agentic.actors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple test class to run the Graddie grading system
 */
public class GraddieTest {
    private static final Logger logger = LoggerFactory.getLogger(GraddieTest.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    GRADDIE TEST                            ║");
        System.out.println("║                Grading System Test                         ║");
        System.out.println("║                    Version 2.0.0                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Create actor system
        Behavior<Void> rootBehavior = Behaviors.setup(context -> {
            // Create a listener actor to capture the final result
            ActorRef<GraddieMessages.Message> resultListener = context.spawn(
                Behaviors.receive(GraddieMessages.Message.class)
                    .onMessage(GraddieMessages.GradingComplete.class, msg -> {
                        System.out.println();
                        System.out.println("🎉 GRADING COMPLETED SUCCESSFULLY!");
                        System.out.println("📋 Student: " + msg.getStudentId());
                        System.out.println("📝 Assignment: " + msg.getAssignmentName());
                        System.out.println("📊 Score: " + msg.getTotalScore() + "/" + msg.getMaxPossibleScore());
                        System.out.println("🎯 Grade: " + msg.getGrade());
                        System.out.println("💬 Feedback: " + msg.getOverallFeedback());
                        System.out.println();
                        
                        // Stop the system after displaying results
                        context.getSystem().terminate();
                        return Behaviors.stopped();
                    })
                    .onMessage(GraddieMessages.GradingFailed.class, msg -> {
                        System.err.println("❌ Grading failed: " + msg.getError());
                        context.getSystem().terminate();
                        return Behaviors.stopped();
                    })
                    .build(),
                "result-listener"
            );

            // Create the grading coordinator
            ActorRef<GraddieMessages.Message> coordinator = context.spawn(
                GradingCoordinatorActor.create(),
                "grading-coordinator"
            );

            // Create the submission receiver
            ActorRef<GraddieMessages.Message> submissionReceiver = context.spawn(
                SubmissionReceiverActor.create(coordinator),
                "submission-receiver"
            );

            System.out.println("✅ System initialized");
            System.out.println("📥 Submission receiver ready");
            System.out.println("🎯 Grading coordinator ready");
            System.out.println();

            // Start the grading process after a short delay
            context.getSystem().scheduler().scheduleOnce(
                java.time.Duration.ofSeconds(2),
                () -> {
                    System.out.println("🚀 Starting automatic grading test...");
                    System.out.println();
                    
                    // Send a test submission
                    submissionReceiver.tell(new GraddieMessages.StudentSubmission(
                        "STUDENT001",
                        "Essay Assignment",
                        "The Impact of Artificial Intelligence on Modern Education\n\n" +
                        "Introduction\n" +
                        "Artificial Intelligence (AI) has emerged as a transformative force in modern education, " +
                        "fundamentally changing how students learn and how educators teach. This essay explores " +
                        "the multifaceted impact of AI on educational systems, examining both its benefits and " +
                        "potential challenges.\n\n" +
                        "Content Quality and Depth\n" +
                        "AI technologies have revolutionized content delivery through personalized learning " +
                        "platforms that adapt to individual student needs. These systems analyze student " +
                        "performance data to provide customized educational experiences, ensuring that each " +
                        "learner receives instruction tailored to their unique learning style and pace.\n\n" +
                        "However, the implementation of AI in education raises important questions about " +
                        "data privacy and the potential for algorithmic bias. Educational institutions must " +
                        "carefully consider these ethical implications while harnessing AI's capabilities.\n\n" +
                        "Organization and Structure\n" +
                        "The essay demonstrates clear organization with a logical flow from introduction " +
                        "to conclusion. Each paragraph builds upon the previous one, creating a coherent " +
                        "argument about AI's role in education. The use of transitional phrases and " +
                        "topic sentences helps guide the reader through the complex subject matter.\n\n" +
                        "Critical Thinking and Analysis\n" +
                        "The analysis goes beyond surface-level observations to explore deeper implications. " +
                        "The author considers multiple perspectives, acknowledging both the benefits and " +
                        "potential drawbacks of AI in education. This balanced approach demonstrates " +
                        "sophisticated critical thinking skills and the ability to evaluate complex issues " +
                        "from multiple angles.\n\n" +
                        "Conclusion\n" +
                        "While AI presents unprecedented opportunities for educational enhancement, its " +
                        "successful integration requires careful consideration of ethical, practical, and " +
                        "pedagogical factors. The future of education lies in finding the right balance " +
                        "between technological innovation and human-centered learning principles.",
                        com.agentic.actors.GraddieMessages.QuestionType.ESSAY,
                        null
                    ));
                },
                context.getSystem().executionContext()
            );

            return Behaviors.empty();
        });

        // Create actor system
        ActorSystem<Void> system = ActorSystem.create(rootBehavior, "GraddieTest");

        System.out.println("✅ Graddie test system started");
        System.out.println("⏳ Running automatic grading test...");
        System.out.println();

        // Keep the system running
        try {
            // Wait for completion
            Thread.sleep(30000); // Wait up to 30 seconds
            System.out.println("⏰ Test timeout reached");
            system.terminate();
        } catch (Exception e) {
            System.err.println("❌ System terminated unexpectedly: " + e.getMessage());
        }
    }
} 