package com.agentic.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.utils.OpenAIClient;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Actor responsible for generating overall feedback based on all category evaluations
 */
public class FeedbackActor extends AbstractBehavior<GradingMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(FeedbackActor.class);
    
    private final OpenAIClient openAIClient;

    private FeedbackActor(ActorContext<GradingMessages.Message> context) {
        super(context);
        
        // Initialize OpenAI client
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("OPENAI_API_KEY not set, using mock client for feedback generation");
            this.openAIClient = createMockClient();
        } else {
            this.openAIClient = new OpenAIClient(apiKey);
        }
        
        logger.info("FeedbackActor initialized");
    }

    public static Behavior<GradingMessages.Message> create() {
        return Behaviors.setup(FeedbackActor::new);
    }

    @Override
    public Receive<GradingMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GradingMessages.GenerateFeedback.class, this::onGenerateFeedback)
                .build();
    }

    private Behavior<GradingMessages.Message> onGenerateFeedback(GradingMessages.GenerateFeedback msg) {
        logger.info("Generating overall feedback for {} categories", msg.getCategoryEvaluations().size());
        
        // Start async feedback generation
        CompletableFuture<String> feedbackFuture = openAIClient.generateOverallFeedback(
            msg.getSubmissionContent(), 
            msg.getCategoryEvaluations()
        );
        
        // Handle the result asynchronously
        feedbackFuture.thenAccept(feedback -> {
            logger.info("Overall feedback generated successfully");
            getContext().getSelf().tell(new GradingMessages.FeedbackGenerated(feedback));
        }).exceptionally(throwable -> {
            logger.error("Feedback generation failed: {}", throwable.getMessage());
            getContext().getSelf().tell(new GradingMessages.FeedbackGenerationFailed(throwable.getMessage()));
            return null;
        });
        
        return this;
    }

    /**
     * Create a mock OpenAI client for testing when API key is not available
     */
    private OpenAIClient createMockClient() {
        return new OpenAIClient("mock-key") {
            @Override
            public CompletableFuture<String> generateOverallFeedback(
                    String submissionContent,
                    Map<String, GradingEvaluation> categoryEvaluations) {
                
                return CompletableFuture.supplyAsync(() -> {
                    logger.info("Mock feedback generation for {} categories", categoryEvaluations.size());
                    
                    StringBuilder feedback = new StringBuilder();
                    feedback.append("OVERALL ASSESSMENT\n\n");
                    
                    // Calculate overall statistics
                    int totalScore = 0;
                    int maxPossibleScore = 0;
                    int excellentCount = 0;
                    int goodCount = 0;
                    int fairCount = 0;
                    int needsImprovementCount = 0;
                    
                    for (GradingEvaluation eval : categoryEvaluations.values()) {
                        totalScore += eval.score();
                        maxPossibleScore += eval.maxPoints();
                        
                        switch (eval.scoreBand()) {
                            case "Excellent" -> excellentCount++;
                            case "Good" -> goodCount++;
                            case "Fair" -> fairCount++;
                            case "Needs Improvement" -> needsImprovementCount++;
                        }
                    }
                    
                    double overallPercentage = maxPossibleScore > 0 ? 
                        (double) totalScore / maxPossibleScore * 100.0 : 0.0;
                    
                    // Generate overall assessment
                    feedback.append("Overall Performance: ").append(String.format("%.1f%%", overallPercentage)).append("\n");
                    feedback.append("Total Score: ").append(totalScore).append("/").append(maxPossibleScore).append(" points\n\n");
                    
                    // Performance summary
                    feedback.append("PERFORMANCE SUMMARY:\n");
                    if (excellentCount > 0) {
                        feedback.append("- ").append(excellentCount).append(" category(ies) rated as Excellent\n");
                    }
                    if (goodCount > 0) {
                        feedback.append("- ").append(goodCount).append(" category(ies) rated as Good\n");
                    }
                    if (fairCount > 0) {
                        feedback.append("- ").append(fairCount).append(" category(ies) rated as Fair\n");
                    }
                    if (needsImprovementCount > 0) {
                        feedback.append("- ").append(needsImprovementCount).append(" category(ies) need improvement\n");
                    }
                    feedback.append("\n");
                    
                    // Key strengths
                    feedback.append("KEY STRENGTHS:\n");
                    if (excellentCount > 0) {
                        feedback.append("- Demonstrated exceptional performance in ").append(excellentCount).append(" area(s)\n");
                    }
                    if (goodCount > 0) {
                        feedback.append("- Showed solid understanding in ").append(goodCount).append(" area(s)\n");
                    }
                    feedback.append("- Submitted work with ").append(submissionContent.split("\\s+").length).append(" words\n");
                    feedback.append("\n");
                    
                    // Areas for improvement
                    feedback.append("AREAS FOR IMPROVEMENT:\n");
                    if (needsImprovementCount > 0) {
                        feedback.append("- Focus on improving ").append(needsImprovementCount).append(" category(ies)\n");
                    }
                    if (fairCount > 0) {
                        feedback.append("- Enhance performance in ").append(fairCount).append(" area(s)\n");
                    }
                    feedback.append("\n");
                    
                    // Recommendations
                    feedback.append("RECOMMENDATIONS:\n");
                    if (overallPercentage >= 90) {
                        feedback.append("- Excellent work! Continue to maintain this high standard.\n");
                    } else if (overallPercentage >= 80) {
                        feedback.append("- Good work overall. Focus on the areas that need improvement.\n");
                    } else if (overallPercentage >= 70) {
                        feedback.append("- Fair performance. Consider seeking additional help in weaker areas.\n");
                    } else {
                        feedback.append("- Significant improvement needed. Consider reviewing the assignment requirements.\n");
                    }
                    
                    feedback.append("- Review feedback for each category to understand specific areas for growth.\n");
                    feedback.append("- Consider seeking clarification on any unclear requirements.\n");
                    
                    return feedback.toString();
                });
            }
        };
    }
} 