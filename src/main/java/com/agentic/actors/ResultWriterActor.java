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
    private final ActorRef<GraddieMessages.Message> csvHelper;
    
    private ResultWriterActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> coordinator) {
        super(context);
        this.coordinator = coordinator;
        
        // Create a CSV helper actor for FORWARD pattern demonstration
        this.csvHelper = context.spawn(createCsvHelper(), "csv-helper");
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
        // FORWARD PATTERN: Delegate CSV writing to helper actor
        logger.debug("🔄 FORWARD pattern: Delegating CSV writing to helper actor");
        csvHelper.tell(msg);
        
        return this;
    }
    
    /**
     * Creates a CSV helper actor for FORWARD pattern demonstration
     */
    private Behavior<GraddieMessages.Message> createCsvHelper() {
        return Behaviors.receive((context, message) -> {
            if (message instanceof GraddieMessages.WriteResults) {
                GraddieMessages.WriteResults msg = (GraddieMessages.WriteResults) message;
                logger.debug("🔄 FORWARD pattern: Helper processing forwarded CSV write request");
                
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
                    
                    // Send response back to coordinator (preserving the forwarding chain)
                    coordinator.tell(new GraddieMessages.ResultsWritten(filePath));
                    
                } catch (IOException e) {
                    logger.error("Helper failed to write results: {}", e.getMessage());
                    coordinator.tell(new GraddieMessages.ResultsWriteFailed(e.getMessage()));
                } catch (Exception e) {
                    logger.error("Helper unexpected error writing results: {}", e.getMessage());
                    coordinator.tell(new GraddieMessages.ResultsWriteFailed("Unexpected error: " + e.getMessage()));
                }
                
                return Behaviors.same();
            }
            return Behaviors.unhandled();
        });
    }
} 