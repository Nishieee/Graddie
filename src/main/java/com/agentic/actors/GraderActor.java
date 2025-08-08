package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.models.RubricItem;
import com.agentic.utils.OpenAIClient;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Actor responsible for grading a specific rubric category using OpenAI
 */
public class GraderActor extends AbstractBehavior<GradingMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(GraderActor.class);
    
    private final String category;
    private final OpenAIClient openAIClient;
    private ActorRef<GradingMessages.Message> replyTo;

    private GraderActor(ActorContext<GradingMessages.Message> context, String category) {
        super(context);
        this.category = category;
        
        // Initialize OpenAI client (you would typically get API key from environment or config)
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("OPENAI_API_KEY not set, using mock client for category: {}", category);
            this.openAIClient = createMockClient();
        } else {
            this.openAIClient = new OpenAIClient(apiKey);
        }
        
        logger.info("GraderActor initialized for category: {}", category);
    }

    public static Behavior<GradingMessages.Message> create(String category) {
        return Behaviors.setup(context -> new GraderActor(context, category));
    }

    @Override
    public Receive<GradingMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GradingMessages.GradeCategory.class, this::onGradeCategory)
                .build();
    }

    private Behavior<GradingMessages.Message> onGradeCategory(GradingMessages.GradeCategory msg) {
        System.out.println("📋 Starting grading for category: " + msg.getCategory());
        
        // Create score bands map
        Map<String, RubricItem.ScoreBand> scoreBands = new HashMap<>();
        if (msg.getRubricItem().getExcellent() != null) {
            scoreBands.put("Excellent", msg.getRubricItem().getExcellent());
        }
        if (msg.getRubricItem().getGood() != null) {
            scoreBands.put("Good", msg.getRubricItem().getGood());
        }
        if (msg.getRubricItem().getFair() != null) {
            scoreBands.put("Fair", msg.getRubricItem().getFair());
        }
        if (msg.getRubricItem().getNeedsImprovement() != null) {
            scoreBands.put("Needs Improvement", msg.getRubricItem().getNeedsImprovement());
        }
        
        try {
            // Use synchronous evaluation for now
            GradingEvaluation evaluation = openAIClient.evaluateSubmissionSync(
                msg.getSubmissionContent(),
                msg.getCategory(),
                msg.getRubricItem().getDescription(),
                msg.getRubricItem().getMaxPoints(),
                scoreBands,
                com.agentic.actors.GraddieMessages.QuestionType.ESSAY,
                null
            );
            
            System.out.println("✅ Evaluation completed for category " + msg.getCategory() + ": " + evaluation.score() + "/" + evaluation.maxPoints());
            getContext().getSelf().tell(new GradingMessages.CategoryGraded(evaluation));
            
        } catch (Exception e) {
            System.err.println("❌ Evaluation failed for category " + msg.getCategory() + ": " + e.getMessage());
            getContext().getSelf().tell(new GradingMessages.CategoryGradingFailed(msg.getCategory(), e.getMessage()));
        }
        
        return this;
    }

    /**
     * Create a mock OpenAI client for testing when API key is not available
     */
    private OpenAIClient createMockClient() {
        return new OpenAIClient("mock-key") {
            @Override
            public CompletableFuture<GradingEvaluation> evaluateSubmission(
                    String submissionContent,
                    String rubricCategory,
                    String rubricDescription,
                    int maxPoints,
                    Map<String, RubricItem.ScoreBand> scoreBands) {
                
                return CompletableFuture.supplyAsync(() -> {
                    logger.info("Mock evaluation for category: {}", rubricCategory);
                    
                    // Simple mock logic based on content length and category
                    int mockScore;
                    String mockScoreBand;
                    String mockFeedback;
                    
                    int wordCount = submissionContent.split("\\s+").length;
                    
                    if (wordCount > 500) {
                        mockScore = maxPoints;
                        mockScoreBand = "Excellent";
                        mockFeedback = "Excellent work with comprehensive coverage and depth.";
                    } else if (wordCount > 300) {
                        mockScore = (int) (maxPoints * 0.8);
                        mockScoreBand = "Good";
                        mockFeedback = "Good work with solid coverage and some depth.";
                    } else if (wordCount > 150) {
                        mockScore = (int) (maxPoints * 0.6);
                        mockScoreBand = "Fair";
                        mockFeedback = "Fair work with adequate coverage but limited depth.";
                    } else {
                        mockScore = (int) (maxPoints * 0.4);
                        mockScoreBand = "Needs Improvement";
                        mockFeedback = "Work needs improvement with limited coverage and depth.";
                    }
                    
                    // Add category-specific adjustments
                    switch (rubricCategory.toLowerCase()) {
                        case "content quality":
                            if (submissionContent.contains("analysis") || submissionContent.contains("discussion")) {
                                mockScore = Math.min(mockScore + 5, maxPoints);
                            }
                            break;
                        case "organization":
                            if (submissionContent.contains("introduction") && submissionContent.contains("conclusion")) {
                                mockScore = Math.min(mockScore + 3, maxPoints);
                            }
                            break;
                        case "critical thinking":
                            if (submissionContent.contains("however") || submissionContent.contains("although")) {
                                mockScore = Math.min(mockScore + 4, maxPoints);
                            }
                            break;
                        case "mechanics":
                            // Simple grammar check
                            if (submissionContent.matches(".*[.!?]\\s+[A-Z].*")) {
                                mockScore = Math.min(mockScore + 2, maxPoints);
                            }
                            break;
                    }
                    
                    return new GradingEvaluation(rubricCategory, mockScore, maxPoints, mockFeedback, mockScoreBand);
                });
            }
        };
    }
} 