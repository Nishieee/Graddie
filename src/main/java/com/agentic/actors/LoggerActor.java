package com.agentic.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for logging messages for debugging and auditing
 */
public class LoggerActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(LoggerActor.class);
    
    private LoggerActor(ActorContext<GraddieMessages.Message> context) {
        super(context);

    }
    
    public static Behavior<GraddieMessages.Message> create() {
        return Behaviors.setup(LoggerActor::new);
    }
    
    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.LogMessage.class, this::onLogMessage)
                .build();
    }
    
    private Behavior<GraddieMessages.Message> onLogMessage(GraddieMessages.LogMessage msg) {
        switch (msg.getLevel().toLowerCase()) {
            case "debug":
                logger.debug("[{}] {}", msg.getSource(), msg.getMessage());
                break;
            case "info":
                logger.info("[{}] {}", msg.getSource(), msg.getMessage());
                break;
            case "warn":
                logger.warn("[{}] {}", msg.getSource(), msg.getMessage());
                break;
            case "error":
                logger.error("[{}] {}", msg.getSource(), msg.getMessage());
                break;
            default:
                logger.info("[{}] {}", msg.getSource(), msg.getMessage());
        }
        
        return this;
    }
} 