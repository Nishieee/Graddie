package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for receiving student submissions and forwarding them to the grading coordinator
 */
public class SubmissionReceiverActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionReceiverActor.class);
    
    private final ActorRef<GraddieMessages.Message> coordinator;
    
    private SubmissionReceiverActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;
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
    
    private Behavior<GraddieMessages.Message> onStudentSubmission(GraddieMessages.StudentSubmission msg) {
        // Send the submission to the coordinator using tell
        coordinator.tell(new GraddieMessages.StartGrading(
            msg.getStudentId(), 
            msg.getAssignmentName(), 
            msg.getSubmissionContent(),
            msg.getQuestionType(),
            msg.getCorrectAnswers()
        ));
        
        return this;
    }
} 