package com.agentic.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.models.Submission;
import com.agentic.utils.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Actor responsible for loading student submissions from text files
 */
public class SubmissionReaderActor extends AbstractBehavior<GradingMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionReaderActor.class);

    private SubmissionReaderActor(ActorContext<GradingMessages.Message> context) {
        super(context);
    }

    public static Behavior<GradingMessages.Message> create() {
        return Behaviors.setup(SubmissionReaderActor::new);
    }

    @Override
    public Receive<GradingMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GradingMessages.LoadSubmission.class, this::onLoadSubmission)
                .build();
    }

    private Behavior<GradingMessages.Message> onLoadSubmission(GradingMessages.LoadSubmission msg) {
        logger.info("Loading submission from: {}", msg.getFilePath());
        
        try {
            String content = CsvUtils.readSubmissionFromFile(msg.getFilePath());
            
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Empty submission content from {}", msg.getFilePath());
                getContext().getSelf().tell(new GradingMessages.SubmissionLoadFailed("Empty submission content"));
            } else {
                // Create submission object with default values
                Submission submission = new Submission(
                    "STUDENT001", // Default student ID
                    "Essay Assignment", // Default assignment name
                    content,
                    msg.getFilePath()
                );
                
                logger.info("Successfully loaded submission: {} characters, {} words", 
                           content.length(), submission.getWordCount());
                getContext().getSelf().tell(new GradingMessages.SubmissionLoaded(submission));
            }
            
        } catch (IOException e) {
            logger.error("Failed to load submission from {}: {}", msg.getFilePath(), e.getMessage());
            getContext().getSelf().tell(new GradingMessages.SubmissionLoadFailed(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error loading submission from {}: {}", msg.getFilePath(), e.getMessage());
            getContext().getSelf().tell(new GradingMessages.SubmissionLoadFailed("Unexpected error: " + e.getMessage()));
        }
        
        return this;
    }
} 