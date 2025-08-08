package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.agentic.utils.CsvUtils;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Actor responsible for writing grading results to CSV and console
 */
public class ResultWriterActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(ResultWriterActor.class);
    
    private final ActorRef<GraddieMessages.Message> coordinator;
    
    private ResultWriterActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;

    }
    
    public static Behavior<GraddieMessages.Message> create(ActorRef<GraddieMessages.Message> coordinator) {
        return Behaviors.setup(context -> new ResultWriterActor(context, coordinator));
    }

    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.WriteResults.class, this::onWriteResults)
                .build();
    }

    private Behavior<GraddieMessages.Message> onWriteResults(GraddieMessages.WriteResults msg) {

        
        try {
            // Create GradingResult for CSV writing
            List<com.agentic.models.GradingResult> results = new ArrayList<>();
            com.agentic.models.GradingResult result = new com.agentic.models.GradingResult(
                msg.getStudentId(), msg.getAssignmentName()
            );
            result.setTotalScore(msg.getTotalScore());
            result.setMaxPossibleScore(msg.getMaxPossibleScore());
            result.setGrade(msg.getGrade());
            result.setOverallFeedback(msg.getOverallFeedback());
            result.setGradedAt(LocalDateTime.now());
            
            // Add category scores
            Map<String, com.agentic.models.GradingResult.CategoryScore> categoryScores = new java.util.HashMap<>();
            for (GradingEvaluation evaluation : msg.getCategoryEvaluations().values()) {
                categoryScores.put(evaluation.category(), new com.agentic.models.GradingResult.CategoryScore(
                    evaluation.category(),
                    evaluation.score(),
                    evaluation.maxPoints(),
                    evaluation.feedback(),
                    evaluation.scoreBand()
                ));
            }
            result.setCategoryScores(categoryScores);
            
            results.add(result);
            
            String filePath = "grading_results.csv";
            CsvUtils.writeGradingResultsToCsv(filePath, results);
            

            
    
            coordinator.tell(new GraddieMessages.ResultsWritten(filePath));
            
        } catch (IOException e) {
            logger.error("Failed to write results: {}", e.getMessage());
            System.err.println("❌ Failed to write results: " + e.getMessage());
            coordinator.tell(new GraddieMessages.ResultsWriteFailed(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error writing results: {}", e.getMessage());
            System.err.println("❌ Unexpected error writing results: " + e.getMessage());
            coordinator.tell(new GraddieMessages.ResultsWriteFailed("Unexpected error: " + e.getMessage()));
        }
        
        return this;
    }
} 