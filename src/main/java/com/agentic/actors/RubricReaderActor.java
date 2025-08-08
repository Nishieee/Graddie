package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.models.RubricItem;
import com.agentic.utils.CsvUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Actor responsible for reading and parsing rubric files
 */
public class RubricReaderActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(RubricReaderActor.class);
    
    private final ActorRef<GraddieMessages.Message> parent;

    private RubricReaderActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> parent) {
        super(context);
        this.parent = parent;

    }

    public static Behavior<GraddieMessages.Message> create(ActorRef<GraddieMessages.Message> parent) {
        return Behaviors.setup(context -> new RubricReaderActor(context, parent));
    }

    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.LoadRubric.class, this::onLoadRubric)
                .build();
    }

    private Behavior<GraddieMessages.Message> onLoadRubric(GraddieMessages.LoadRubric msg) {

        
        try {
            List<RubricItem> rubricItems = CsvUtils.readRubricFromCsv(msg.getRubricPath());

            
            // Send the result to the parent actor
            parent.tell(new GraddieMessages.RubricLoaded(rubricItems));
            
        } catch (IOException e) {
            logger.error("Failed to load rubric from {}: {}", msg.getRubricPath(), e.getMessage());
            parent.tell(new GraddieMessages.RubricLoadFailed(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error loading rubric from {}: {}", msg.getRubricPath(), e.getMessage());
            parent.tell(new GraddieMessages.RubricLoadFailed("Unexpected error: " + e.getMessage()));
        }
        
        return this;
    }
} 