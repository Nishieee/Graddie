package com.agentic;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.agentic.actors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Main application class for the distributed Graddie grading system
 */
public class GraddieMain {
    private static final Logger logger = LoggerFactory.getLogger(GraddieMain.class);

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar graddie.jar <port> <node-type>");
            System.out.println("  port: Port number for the Akka cluster (e.g., 2551, 2552)");
            System.out.println("  node-type: 'coordinator' or 'worker'");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String nodeType = args[1];

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    GRADDIE SYSTEM                          ║");
        System.out.println("║                Distributed Grading System                  ║");
        System.out.println("║                    Version 2.0.0                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("🚀 Starting Graddie node:");
        System.out.println("📍 Port: " + port);
        System.out.println("🏷️  Type: " + nodeType);
        System.out.println();

        // Create actor system based on node type
        Behavior<Void> rootBehavior;
        if ("coordinator".equals(nodeType)) {
            rootBehavior = createCoordinatorNode();
        } else if ("worker".equals(nodeType)) {
            rootBehavior = createWorkerNode();
        } else {
            System.err.println("❌ Invalid node type: " + nodeType);
            System.exit(1);
            return;
        }

        // Load node-specific config
        Config config = "coordinator".equals(nodeType)
            ? ConfigFactory.parseResources("node1.conf").withFallback(ConfigFactory.load())
            : ConfigFactory.parseResources("node2.conf").withFallback(ConfigFactory.load());
        
        // Create actor system with config
        ActorSystem<Void> system = ActorSystem.create(rootBehavior, "GraddieCluster", config);

        System.out.println("✅ Graddie node started successfully");
        System.out.println();

        // Keep the system running
        try {
            if ("coordinator".equals(nodeType)) {
                boolean isInteractive = System.console() != null;
                if (isInteractive) {
                    runInteractiveMode(system);
                } else {
                    System.out.println("🖥️  No TTY detected. Running coordinator in non-interactive mode.");
                    System.out.println("🔄 Coordinator node running. Press Ctrl+C to stop.");
                    Thread.currentThread().join();
                }
            } else {
                // For worker node, just keep running
                System.out.println("🔄 Worker node running. Press Ctrl+C to stop.");
                Thread.currentThread().join();
            }
        } catch (Exception e) {
            System.err.println("❌ System terminated unexpectedly: " + e.getMessage());
        }
    }

    private static Behavior<Void> createCoordinatorNode() {
        return Behaviors.setup(context -> {
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

            System.out.println("✅ Coordinator node initialized");
            System.out.println("📥 Submission receiver ready");
            System.out.println("🎯 Grading coordinator ready");
            System.out.println();

            return Behaviors.empty();
        });
    }

    private static Behavior<Void> createWorkerNode() {
        return Behaviors.setup(context -> {
            System.out.println("✅ Worker node initialized");
            System.out.println("🔄 Ready to receive grading tasks");
            System.out.println();

            // Spawn a pool of workers registered to the receptionist
            for (int i = 0; i < Math.max(2, Runtime.getRuntime().availableProcessors() / 2); i++) {
                context.spawn(GradingWorkerActor.create(), "grading-worker-" + i);
            }
            return Behaviors.empty();
        });
    }

    private static void runInteractiveMode(ActorSystem<Void> system) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("🎯 INTERACTIVE GRADING MODE");
        System.out.println("==========================");
        System.out.println();
        
        while (true) {
            System.out.print("Enter student ID (or 'quit' to exit): ");
            String studentId = scanner.nextLine();
            
            if ("quit".equalsIgnoreCase(studentId)) {
                break;
            }
            
            System.out.print("Enter assignment name: ");
            String assignmentName = scanner.nextLine();
            
            System.out.print("Enter submission content (or 'file' to use default): ");
            String submissionInput = scanner.nextLine();
            
            String submissionContent;
            if ("file".equalsIgnoreCase(submissionInput)) {
                // Use default submission from file
                try {
                    submissionContent = com.agentic.utils.CsvUtils.readSubmissionFromFile(
                        "src/main/resources/assignment_submission.txt"
                    );
                } catch (Exception e) {
                    System.err.println("❌ Failed to read submission file: " + e.getMessage());
                    continue;
                }
            } else {
                submissionContent = submissionInput;
            }
            
            System.out.println();
            System.out.println("📊 Starting grading process...");
            System.out.println("📋 Student: " + studentId);
            System.out.println("📝 Assignment: " + assignmentName);
            System.out.println("📄 Content: " + submissionContent.length() + " characters");
            System.out.println();
            
            // Send submission to the system
            // Note: In a real distributed system, you'd send this to the submission receiver
            // For now, we'll simulate it directly
            System.out.println("🔄 Processing submission...");
            
            // Simulate processing time
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            System.out.println("✅ Grading completed!");
            System.out.println();
        }
        
        scanner.close();
        system.terminate();
    }
} 