package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.utils.OpenAIClient;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Actor responsible for generating overall feedback using LLM
 */
public class LLMActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(LLMActor.class);
    
    private final OpenAIClient openAIClient;
    private final ActorRef<GraddieMessages.Message> coordinator;
    
    private LLMActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;
        
        // Initialize OpenAI client using ApiKeyLoader
        String apiKey = com.agentic.utils.ApiKeyLoader.loadOpenAIKey();
        if (!com.agentic.utils.ApiKeyLoader.hasValidApiKey()) {
            logger.warn("No valid OpenAI API key found, using mock client for feedback generation");
            this.openAIClient = createMockClient();
        } else {
            this.openAIClient = new OpenAIClient(apiKey);
        }
        

    }
    
    public static Behavior<GraddieMessages.Message> create(ActorRef<GraddieMessages.Message> coordinator) {
        return Behaviors.setup(context -> new LLMActor(context, coordinator));
    }
    
    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.GenerateFeedback.class, this::onGenerateFeedback)
                .build();
    }
    
    private Behavior<GraddieMessages.Message> onGenerateFeedback(GraddieMessages.GenerateFeedback msg) {
        try {
            String feedback = openAIClient.generateOverallFeedbackSync(
                msg.getSubmissionContent(),
                msg.getCategoryEvaluations()
            );
            
            // Send feedback back to the coordinator (parent)
            coordinator.tell(new GraddieMessages.FeedbackGenerated(feedback));
            
        } catch (Exception e) {
            logger.error("Failed to generate feedback: {}", e.getMessage());
            coordinator.tell(new GraddieMessages.FeedbackGenerationFailed(e.getMessage()));
        }
        
        return this;
    }
    
    /**
     * Create a mock OpenAI client for testing when API key is not available
     */
    private OpenAIClient createMockClient() {
        return new OpenAIClient("mock-key") {
            @Override
            public String generateOverallFeedbackSync(
                    String submissionContent,
                    Map<String, GradingEvaluation> categoryEvaluations) {
                
                logger.info("Mock feedback generation");
                
                StringBuilder feedback = new StringBuilder();
                feedback.append("Overall Assessment:\n\n");
                
                int totalScore = 0;
                int maxPossibleScore = 0;
                
                for (GradingEvaluation evaluation : categoryEvaluations.values()) {
                    totalScore += evaluation.score();
                    maxPossibleScore += evaluation.maxPoints();
                    
                    feedback.append(evaluation.category()).append(": ")
                           .append(evaluation.score()).append("/").append(evaluation.maxPoints())
                           .append(" (").append(evaluation.scoreBand()).append(")\n");
                    feedback.append("Feedback: ").append(evaluation.feedback()).append("\n\n");
                }
                
                double percentage = maxPossibleScore > 0 ? (double) totalScore / maxPossibleScore * 100 : 0;
                
                feedback.append("Total Score: ").append(totalScore).append("/").append(maxPossibleScore)
                       .append(" (").append(String.format("%.1f%%", percentage)).append(")\n\n");
                
                if (percentage >= 90) {
                    feedback.append("Excellent work overall! Your submission demonstrates strong understanding and execution across all categories.");
                } else if (percentage >= 80) {
                    feedback.append("Good work! Your submission shows solid understanding with room for improvement in some areas.");
                } else if (percentage >= 70) {
                    feedback.append("Fair work. Your submission meets basic requirements but could benefit from more depth and detail.");
                } else {
                    feedback.append("Your submission needs improvement. Consider reviewing the feedback for each category and revising accordingly.");
                }
                
                return feedback.toString();
            }
        };
    }
} 