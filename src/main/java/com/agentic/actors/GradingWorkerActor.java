package com.agentic.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.actor.typed.javadsl.*;
import com.agentic.models.RubricItem;
import com.agentic.utils.OpenAIClient;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Actor responsible for grading a specific rubric category
 */
public class GradingWorkerActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(GradingWorkerActor.class);
    
    private final OpenAIClient openAIClient;
    public static final ServiceKey<GraddieMessages.Message> WORKER_SERVICE_KEY =
            ServiceKey.create(GraddieMessages.Message.class, "grading-worker");
    
    private GradingWorkerActor(ActorContext<GraddieMessages.Message> context) {
        super(context);
        // Register with receptionist for cluster discovery
        context.getSystem().receptionist().tell(Receptionist.register(WORKER_SERVICE_KEY, context.getSelf()));

        // Initialize OpenAI client using ApiKeyLoader
        String apiKey = com.agentic.utils.ApiKeyLoader.loadOpenAIKey();
        if (!com.agentic.utils.ApiKeyLoader.hasValidApiKey()) {
            logger.warn("No valid OpenAI API key found, using mock client for worker");
            this.openAIClient = createMockClient();
        } else {
            String baseUrl = com.agentic.utils.ApiKeyLoader.loadOpenAIBaseUrl();
            String model = com.agentic.utils.ApiKeyLoader.loadOpenAIModel();
            this.openAIClient = new OpenAIClient(apiKey, baseUrl, model);
        }
    }
    
    public static Behavior<GraddieMessages.Message> create() {
        return Behaviors.setup(GradingWorkerActor::new);
    }
    
    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.GradeCategory.class, this::onGradeCategory)
                .build();
    }
    
        private Behavior<GraddieMessages.Message> onGradeCategory(GraddieMessages.GradeCategory msg) {
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
            // For MCQ questions, calculate actual score first
            OpenAIClient.MCQScore mcqScore = null;
            if (msg.getQuestionType() == com.agentic.actors.GraddieMessages.QuestionType.MCQ && 
                msg.getCorrectAnswers() != null && !msg.getCorrectAnswers().isEmpty()) {
                
                mcqScore = openAIClient.calculateMCQScore(msg.getSubmissionContent(), msg.getCorrectAnswers());
            }
            
            // Use synchronous evaluation with question type and correct answers
            GradingEvaluation evaluation = openAIClient.evaluateSubmissionSync(
                msg.getSubmissionContent(),
                msg.getCategory(),
                msg.getRubricItem().getDescription(),
                msg.getRubricItem().getMaxPoints(),
                scoreBands,
                msg.getQuestionType(),
                msg.getCorrectAnswers()
            );
            
            // For MCQ questions, replace the evaluation with actual MCQ score and detailed feedback
            if (mcqScore != null) {
                // Calculate actual MCQ score: (correct_answers / total_questions) * max_points
                int actualMCQScore = (int) Math.round((double) mcqScore.correctAnswers() / mcqScore.totalQuestions() * evaluation.maxPoints());
                
                evaluation = new GradingEvaluation(
                    evaluation.category(),
                    actualMCQScore,
                    evaluation.maxPoints(),
                    mcqScore.getDetailedFeedback(),
                    evaluation.scoreBand()
                );
            }
            
            // Send result back to replyTo (router/coordinator)
            if (msg.getReplyTo() != null) {
                msg.getReplyTo().tell(new GraddieMessages.CategoryGraded(evaluation));
            }
            
        } catch (Exception e) {
            logger.error("Evaluation failed for category {}: {}", msg.getCategory(), e.getMessage());
            System.err.println("❌ LLM evaluation failed for category " + msg.getCategory() + ": " + e.getMessage());
            
            if (msg.getReplyTo() != null) {
                msg.getReplyTo().tell(new GraddieMessages.CategoryGradingFailed(msg.getCategory(), e.getMessage()));
            }
        }
        
        return this;
    }
    
    /**
     * Create a mock OpenAI client for testing when API key is not available
     */
    private OpenAIClient createMockClient() {
        return new OpenAIClient("mock-key") {
            @Override
            public GradingEvaluation evaluateSubmissionSync(
                    String submissionContent,
                    String rubricCategory,
                    String rubricDescription,
                    int maxPoints,
                    Map<String, RubricItem.ScoreBand> scoreBands,
                    com.agentic.actors.GraddieMessages.QuestionType questionType,
                    String correctAnswers) {
                
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
            }
        };
    }
} 