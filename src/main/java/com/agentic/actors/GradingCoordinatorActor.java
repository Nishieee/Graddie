package com.agentic.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.actor.typed.pubsub.Topic;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.javadsl.Routers;
import akka.pattern.Patterns;
import com.agentic.models.RubricItem;
import com.agentic.utils.CsvUtils;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Actor that coordinates the entire grading process
 */
public class GradingCoordinatorActor extends AbstractBehavior<GraddieMessages.Message> {
    private static final Logger logger = LoggerFactory.getLogger(GradingCoordinatorActor.class);
    
    private final ActorRef<GraddieMessages.Message> rubricReader;
    private final ActorRef<GraddieMessages.Message> llmActor;
    private final ActorRef<GraddieMessages.Message> resultWriter;
    private final ActorRef<GraddieMessages.Message> loggerActor;
    private ActorRef<GraddieMessages.Message> workerRouter;
    
    private List<RubricItem> rubricItems;
    private String submissionContent;
    private String studentId;
    private String assignmentName;
    private GraddieMessages.QuestionType questionType;
    private String correctAnswers;
    private Map<String, GradingEvaluation> categoryEvaluations;
    private int pendingGradings;
    private ActorRef<GraddieMessages.Message> replyTo;
    private String generatedFeedback;
    private String detailedMCQFeedback;
    private String detailedFeedback;
    
    private GradingCoordinatorActor(ActorContext<GraddieMessages.Message> context, ActorRef<GraddieMessages.Message> replyTo) {
        super(context);
        this.replyTo = replyTo;
        this.rubricReader = context.spawn(RubricReaderActor.create(context.getSelf()), "rubric-reader");
        this.llmActor = context.spawn(LLMActor.create(context.getSelf()), "llm-actor");
        this.resultWriter = context.spawn(ResultWriterActor.create(context.getSelf()), "result-writer");
        this.loggerActor = context.spawn(LoggerActor.create(), "logger-actor");
        // Group router over all discovered workers across the cluster
        this.workerRouter = context.spawn(
            Routers.group(GradingWorkerActor.WORKER_SERVICE_KEY),
            "grading-worker-router"
        );

    }
    
    public static Behavior<GraddieMessages.Message> create() {
        return create(null);
    }
    
    public static Behavior<GraddieMessages.Message> create(ActorRef<GraddieMessages.Message> replyTo) {
        return Behaviors.setup(context -> new GradingCoordinatorActor(context, replyTo));
    }
    
    @Override
    public Receive<GraddieMessages.Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(GraddieMessages.StartGrading.class, this::onStartGrading)
                .onMessage(GraddieMessages.RubricLoaded.class, this::onRubricLoaded)
                .onMessage(GraddieMessages.RubricLoadFailed.class, this::onRubricLoadFailed)
                .onMessage(GraddieMessages.CategoryGraded.class, this::onCategoryGraded)
                .onMessage(GraddieMessages.CategoryGradingFailed.class, this::onCategoryGradingFailed)
                .onMessage(GraddieMessages.FeedbackGenerated.class, this::onFeedbackGenerated)
                .onMessage(GraddieMessages.FeedbackGenerationFailed.class, this::onFeedbackGenerationFailed)
                .onMessage(GraddieMessages.ResultsWritten.class, this::onResultsWritten)
                .onMessage(GraddieMessages.ResultsWriteFailed.class, this::onResultsWriteFailed)
                .build();
    }
    
        private Behavior<GraddieMessages.Message> onStartGrading(GraddieMessages.StartGrading msg) {
        this.studentId = msg.getStudentId();
        this.assignmentName = msg.getAssignmentName();
        this.submissionContent = msg.getSubmissionContent();
        this.questionType = msg.getQuestionType();
        this.correctAnswers = msg.getCorrectAnswers();
        this.categoryEvaluations = new HashMap<>();
        this.pendingGradings = 0;
        this.detailedMCQFeedback = "";

        // Load rubric
        rubricReader.tell(new GraddieMessages.LoadRubric("src/main/resources/final_rubric.csv"));

        return this;
    }
    
    private Behavior<GraddieMessages.Message> onRubricLoaded(GraddieMessages.RubricLoaded msg) {
        this.rubricItems = msg.getRubricItems();
        startCategoryGrading();
        return this;
    }
    
    private Behavior<GraddieMessages.Message> onRubricLoadFailed(GraddieMessages.RubricLoadFailed msg) {
        logger.error("Failed to load rubric: {}", msg.getError());
        System.err.println("❌ Failed to load rubric: " + msg.getError());
        
        if (replyTo != null) {
            replyTo.tell(new GraddieMessages.GradingFailed("Rubric load failed: " + msg.getError()));
        }
        
        return this;
    }
    
    private void startCategoryGrading() {
        for (RubricItem rubricItem : rubricItems) {
            String category = rubricItem.getCategory();
            pendingGradings++;
            // Dispatch to any available worker in the cluster via receptionist group router
            workerRouter.tell(new GraddieMessages.GradeCategory(
                category,
                submissionContent,
                rubricItem,
                questionType,
                correctAnswers,
                getContext().getSelf()
            ));
        }
    }
    
    private Behavior<GraddieMessages.Message> onCategoryGraded(GraddieMessages.CategoryGraded msg) {
        GradingEvaluation evaluation = msg.getEvaluation();
        
        // If this is an MCQ evaluation and we have detailed feedback, store it
        if (questionType == GraddieMessages.QuestionType.MCQ && evaluation.feedback().contains("Here's the breakdown")) {
            this.detailedMCQFeedback = evaluation.feedback();
        }
        
        // If this is a Short Answer evaluation and we have detailed feedback, store it
        if (questionType == GraddieMessages.QuestionType.SHORT_ANSWER && evaluation.detailedFeedback() != null) {
            this.detailedFeedback = evaluation.detailedFeedback();
        }
        
        categoryEvaluations.put(evaluation.category(), evaluation);
        pendingGradings--;
        
        checkGradingComplete();
        
        return this;
    }
    
    private Behavior<GraddieMessages.Message> onCategoryGradingFailed(GraddieMessages.CategoryGradingFailed msg) {
        logger.error("Category grading failed for {}: {}", msg.getCategory(), msg.getError());
        System.err.println("❌ Category grading failed for " + msg.getCategory() + ": " + msg.getError());
        
        pendingGradings--;
        
        // Create a default evaluation for failed category
        RubricItem rubricItem = rubricItems.stream()
            .filter(item -> item.getCategory().equals(msg.getCategory()))
            .findFirst()
            .orElse(null);
        
        if (rubricItem != null) {
            GradingEvaluation defaultEvaluation = new GradingEvaluation(
                msg.getCategory(), 0, rubricItem.getMaxPoints(),
                "Grading failed: " + msg.getError(), "Needs Improvement"
            );
            categoryEvaluations.put(msg.getCategory(), defaultEvaluation);
        }
        
        checkGradingComplete();
        
        return this;
    }
    
    private void checkGradingComplete() {
        if (pendingGradings == 0) {
            // Use tell pattern to get feedback from LLM
            llmActor.tell(new GraddieMessages.GenerateFeedback(submissionContent, categoryEvaluations));
        }
    }
    
    private Behavior<GraddieMessages.Message> onFeedbackGenerated(GraddieMessages.FeedbackGenerated msg) {

        // Store the generated feedback
        this.generatedFeedback = msg.getOverallFeedback();
        
        // Calculate final results
        int totalScore = 0;
        int maxPossibleScore = 0;
        
        if (questionType == GraddieMessages.QuestionType.MCQ || questionType == GraddieMessages.QuestionType.SHORT_ANSWER) {
            // For MCQ and Short Answer, use only the first category score (they're single-unit assessments)
            if (!categoryEvaluations.isEmpty()) {
                GradingEvaluation firstEval = categoryEvaluations.values().iterator().next();
                totalScore = firstEval.score();
                maxPossibleScore = 100; // Normalize to 100 for MCQ and Short Answer
            }
        } else {
            // For Essays, sum all category scores (multi-dimensional assessment)
            for (GradingEvaluation evaluation : categoryEvaluations.values()) {
                totalScore += evaluation.score();
                maxPossibleScore += evaluation.maxPoints();
            }
        }
        
        String grade = calculateLetterGrade(totalScore, maxPossibleScore);
        
        // Write results
        resultWriter.tell(new GraddieMessages.WriteResults(
            studentId, assignmentName, totalScore, maxPossibleScore, 
            grade, msg.getOverallFeedback(), categoryEvaluations
        ));
        
        return this;
    }
    
    private Behavior<GraddieMessages.Message> onFeedbackGenerationFailed(GraddieMessages.FeedbackGenerationFailed msg) {
        logger.error("Feedback generation failed: {}", msg.getError());
        System.err.println("❌ Feedback generation failed: " + msg.getError());
        
        // Calculate final results without feedback
        int totalScore = 0;
        int maxPossibleScore = 0;
        
        for (GradingEvaluation evaluation : categoryEvaluations.values()) {
            totalScore += evaluation.score();
            maxPossibleScore += evaluation.maxPoints();
        }
        
        String grade = calculateLetterGrade(totalScore, maxPossibleScore);
        
        resultWriter.tell(new GraddieMessages.WriteResults(
            studentId, assignmentName, totalScore, maxPossibleScore,
            grade, "Feedback generation failed: " + msg.getError(), categoryEvaluations
        ));
        
        return this;
    }
    
    private Behavior<GraddieMessages.Message> onResultsWritten(GraddieMessages.ResultsWritten msg) {

        
        // Calculate final results for completion message
        int totalScore = 0;
        int maxPossibleScore = 0;
        
        for (GradingEvaluation evaluation : categoryEvaluations.values()) {
            totalScore += evaluation.score();
            maxPossibleScore += evaluation.maxPoints();
        }
        
        String grade = calculateLetterGrade(totalScore, maxPossibleScore);
        
        // Send completion message with the actual feedback
        if (replyTo != null) {
            String feedback = (generatedFeedback != null) ? generatedFeedback : "Grading completed successfully. Check the CSV file for detailed results.";
            replyTo.tell(new GraddieMessages.GradingComplete(
                studentId, assignmentName, totalScore, maxPossibleScore, grade, feedback, detailedMCQFeedback, detailedFeedback
            ));
        }
        
        return this;
    }
    
    private Behavior<GraddieMessages.Message> onResultsWriteFailed(GraddieMessages.ResultsWriteFailed msg) {
        logger.error("Results write failed: {}", msg.getError());
        System.err.println("❌ Results write failed: " + msg.getError());
        
        if (replyTo != null) {
            replyTo.tell(new GraddieMessages.GradingFailed("Results write failed: " + msg.getError()));
        }
        
        return this;
    }
    
    private String calculateLetterGrade(int totalScore, int maxPossibleScore) {
        if (maxPossibleScore == 0) return "F";
        
        double percentage = (double) totalScore / maxPossibleScore * 100;
        
        if (percentage >= 90) return "A";
        else if (percentage >= 80) return "B";
        else if (percentage >= 70) return "C";
        else if (percentage >= 60) return "D";
        else return "F";
    }
} 