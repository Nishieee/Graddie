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
            logger.error("No valid OpenAI API key found. Set OPENAI_API_KEY.");
            this.openAIClient = new OpenAIClient(apiKey); // will behave as mock when configured so
        } else {
            this.openAIClient = new OpenAIClient(apiKey, "https://api.openai.com/v1", "gpt-3.5-turbo");
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
            if (!com.agentic.utils.ApiKeyLoader.hasValidApiKey()) {
                String error = "Missing OpenAI API key. Add OPENAI_API_KEY to .env or environment.";
                logger.error(error);
                coordinator.tell(new GraddieMessages.FeedbackGenerationFailed(error));
                return this;
            }
            String feedback = openAIClient.generateOverallFeedbackSync(
                msg.getSubmissionContent(),
                msg.getCategoryEvaluations()
            );
            
            coordinator.tell(new GraddieMessages.FeedbackGenerated(feedback));
            
        } catch (Exception e) {
            logger.error("Failed to generate feedback: {}", e.getMessage());
            coordinator.tell(new GraddieMessages.FeedbackGenerationFailed(e.getMessage()));
        }
        
        return this;
    }
} 