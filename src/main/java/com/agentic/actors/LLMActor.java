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
 * 
 * FORWARD PATTERN NOTE: 
 * Classic Akka had a .forward() method that preserved sender context.
 * In Akka Typed, this doesn't exist - instead we implement the FORWARD pattern
 * concept through delegation and explicit context preservation.
 */
public class LLMActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(LLMActor.class);
    
    private final OpenAIClient openAIClient;
    private final ActorRef<GraddieMessages.Message> coordinator;
    private final ActorRef<GraddieMessages.Message> feedbackHelper;
    
    private LLMActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;
        
        // Create a helper actor for FORWARD pattern demonstration
        this.feedbackHelper = context.spawn(createFeedbackHelper(), "feedback-helper");
        
        // Initialize OpenAI client using ApiKeyLoader
        String apiKey = com.agentic.utils.ApiKeyLoader.loadOpenAIKey();
        if (!com.agentic.utils.ApiKeyLoader.hasValidApiKey()) {
            logger.error("No valid OpenAI API key found. Set OPENAI_API_KEY.");
            this.openAIClient = new OpenAIClient(apiKey);
        } else {
            String baseUrl = com.agentic.utils.ApiKeyLoader.loadOpenAIBaseUrl();
            String model = com.agentic.utils.ApiKeyLoader.loadOpenAIModel();
            this.openAIClient = new OpenAIClient(apiKey, baseUrl, model);
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
        // FORWARD PATTERN: In Akka Typed, there's no .forward() method like in classic Akka
        // Instead, we implement the forward pattern by delegating work while preserving context
        logger.debug("🔄 FORWARD pattern: Delegating to helper while preserving response routing to coordinator");
        
        // This demonstrates the forward pattern concept: delegation without losing context
        feedbackHelper.tell(msg);
        
        return this;
    }
    
    /**
     * Creates a helper actor for FORWARD pattern demonstration
     */
    private Behavior<GraddieMessages.Message> createFeedbackHelper() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof GraddieMessages.GenerateFeedback) {
                GraddieMessages.GenerateFeedback msg = (GraddieMessages.GenerateFeedback) message;
                logger.debug("🔄 FORWARD pattern: Helper processing delegated request (equivalent to classic .forward())");
                
                try {
                    if (!com.agentic.utils.ApiKeyLoader.hasValidApiKey()) {
                        String error = "Missing OpenAI API key. Add OPENAI_API_KEY to .env or environment.";
                        logger.error(error);
                        coordinator.tell(new GraddieMessages.FeedbackGenerationFailed(error));
                        return Behaviors.same();
                    }
                    String feedback = openAIClient.generateOverallFeedbackSync(
                        msg.getSubmissionContent(),
                        msg.getCategoryEvaluations()
                    );
                    
                    // Send response back to coordinator (preserving the forwarding chain)
                    coordinator.tell(new GraddieMessages.FeedbackGenerated(feedback));
                    
                } catch (Exception e) {
                    logger.error("Helper failed to generate feedback: {}", e.getMessage());
                    coordinator.tell(new GraddieMessages.FeedbackGenerationFailed(e.getMessage()));
                }
                
                return Behaviors.same();
            }
            return Behaviors.unhandled();
        });
    }
} 