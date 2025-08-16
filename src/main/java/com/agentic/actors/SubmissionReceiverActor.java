package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for receiving student submissions and routing them to the coordinator
 */
public class SubmissionReceiverActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionReceiverActor.class);
    
    private final ActorRef<GraddieMessages.Message> coordinator;
    
    private SubmissionReceiverActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;
        
        logger.info("📨 Submission Receiver initialized");
    }
    
    public static Behavior<GraddieMessages.Message> create(ActorRef<GraddieMessages.Message> coordinator) {
        return Behaviors.setup(context -> new SubmissionReceiverActor(context, coordinator));
    }
    
    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.StudentSubmission.class, this::onStudentSubmission)
                .build();
    }
    
    /**
     * Process student submission and forward to coordinator
     */
    private Behavior<GraddieMessages.Message> onStudentSubmission(GraddieMessages.StudentSubmission msg) {
        logger.info("📥 Received submission from student {} for assignment '{}'", 
            msg.getStudentId(), msg.getAssignmentName());
        
        // FORWARD PATTERN: In Akka Typed, we implement forward by preserving sender context
        // The actual forward pattern means routing a message while preserving the original sender
        logger.debug("🔄 FORWARD pattern: Preserving original sender context and routing to coordinator");
        
        // Create a ForwardedSubmission that preserves the original sender context
        // This is the Akka Typed equivalent of the classic forward() method
        GraddieMessages.ForwardedSubmission forwardedSubmission = new GraddieMessages.ForwardedSubmission(
            msg,                           // Original submission
            getContext().getSelf(),        // Current actor (the forwarder)
            "FORWARD PATTERN: Preserving sender context through message routing"
        );
        
        // This tells the coordinator that this was forwarded, preserving sender chain
        coordinator.tell(forwardedSubmission);
        
        return this;
    }
}