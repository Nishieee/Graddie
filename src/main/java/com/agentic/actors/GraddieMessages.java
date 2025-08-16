package com.agentic.actors;

import akka.actor.typed.ActorRef;
import com.agentic.models.RubricItem;
import com.agentic.utils.OpenAIClient.GradingEvaluation;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Message types for the distributed Graddie grading system
 */
public class GraddieMessages {
    
    // Base message interface
    public interface Message {
        // Marker interface for all Graddie messages
    }
    
    // Submission messages
    public static class StudentSubmission implements Message {
        private final String studentId;
        private final String assignmentName;
        private final String submissionContent;
        private final QuestionType questionType;
        private final String correctAnswers; // For MCQs and short answers
        
        @JsonCreator
        public StudentSubmission(
                @JsonProperty("studentId") String studentId,
                @JsonProperty("assignmentName") String assignmentName,
                @JsonProperty("submissionContent") String submissionContent,
                @JsonProperty("questionType") QuestionType questionType,
                @JsonProperty("correctAnswers") String correctAnswers) {
            this.studentId = studentId;
            this.assignmentName = assignmentName;
            this.submissionContent = submissionContent;
            this.questionType = questionType;
            this.correctAnswers = correctAnswers;
        }
        
        public String getStudentId() { return studentId; }
        public String getAssignmentName() { return assignmentName; }
        public String getSubmissionContent() { return submissionContent; }
        public QuestionType getQuestionType() { return questionType; }
        public String getCorrectAnswers() { return correctAnswers; }
    }

    public enum QuestionType {
        MCQ,
        SHORT_ANSWER,
        ESSAY
    }
    
    // Grading coordination messages
    public static class StartGrading implements Message {
        private final String studentId;
        private final String assignmentName;
        private final String submissionContent;
        private final QuestionType questionType;
        private final String correctAnswers;
        
        @JsonCreator
        public StartGrading(
                @JsonProperty("studentId") String studentId,
                @JsonProperty("assignmentName") String assignmentName,
                @JsonProperty("submissionContent") String submissionContent,
                @JsonProperty("questionType") QuestionType questionType,
                @JsonProperty("correctAnswers") String correctAnswers) {
            this.studentId = studentId;
            this.assignmentName = assignmentName;
            this.submissionContent = submissionContent;
            this.questionType = questionType;
            this.correctAnswers = correctAnswers;
        }
        
        public String getStudentId() { return studentId; }
        public String getAssignmentName() { return assignmentName; }
        public String getSubmissionContent() { return submissionContent; }
        public QuestionType getQuestionType() { return questionType; }
        public String getCorrectAnswers() { return correctAnswers; }
    }
    
    // Rubric loading messages
    public static class LoadRubric implements Message {
        private final String rubricPath;
        
        @JsonCreator
        public LoadRubric(@JsonProperty("rubricPath") String rubricPath) {
            this.rubricPath = rubricPath;
        }
        
        public String getRubricPath() { return rubricPath; }
    }
    
    public static class RubricLoaded implements Message {
        private final List<RubricItem> rubricItems;
        
        @JsonCreator
        public RubricLoaded(@JsonProperty("rubricItems") List<RubricItem> rubricItems) {
            this.rubricItems = rubricItems;
        }
        
        public List<RubricItem> getRubricItems() { return rubricItems; }
    }
    
    public static class RubricLoadFailed implements Message {
        private final String error;
        
        @JsonCreator
        public RubricLoadFailed(@JsonProperty("error") String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
    
    // Grading worker messages
    public static class GradeCategory implements Message {
        private final String category;
        private final String submissionContent;
        private final RubricItem rubricItem;
        private final QuestionType questionType;
        private final String correctAnswers;
        private final akka.actor.typed.ActorRef<GraddieMessages.Message> replyTo;
        
        @JsonCreator
        public GradeCategory(
                @JsonProperty("category") String category,
                @JsonProperty("submissionContent") String submissionContent,
                @JsonProperty("rubricItem") RubricItem rubricItem,
                @JsonProperty("questionType") QuestionType questionType,
                @JsonProperty("correctAnswers") String correctAnswers,
                @JsonProperty("replyTo") akka.actor.typed.ActorRef<GraddieMessages.Message> replyTo) {
            this.category = category;
            this.submissionContent = submissionContent;
            this.rubricItem = rubricItem;
            this.questionType = questionType;
            this.correctAnswers = correctAnswers;
            this.replyTo = replyTo;
        }
        
        public String getCategory() { return category; }
        public String getSubmissionContent() { return submissionContent; }
        public RubricItem getRubricItem() { return rubricItem; }
        public QuestionType getQuestionType() { return questionType; }
        public String getCorrectAnswers() { return correctAnswers; }
        public akka.actor.typed.ActorRef<GraddieMessages.Message> getReplyTo() { return replyTo; }
    }
    
    public static class CategoryGraded implements Message {
        private final GradingEvaluation evaluation;
        
        @JsonCreator
        public CategoryGraded(@JsonProperty("evaluation") GradingEvaluation evaluation) {
            this.evaluation = evaluation;
        }
        
        public GradingEvaluation getEvaluation() { return evaluation; }
    }
    
    public static class CategoryGradingFailed implements Message {
        private final String category;
        private final String error;
        
        @JsonCreator
        public CategoryGradingFailed(
                @JsonProperty("category") String category,
                @JsonProperty("error") String error) {
            this.category = category;
            this.error = error;
        }
        
        public String getCategory() { return category; }
        public String getError() { return error; }
    }
    
    // LLM feedback messages
    public static class GenerateFeedback implements Message {
        private final String submissionContent;
        private final Map<String, GradingEvaluation> categoryEvaluations;
        
        @JsonCreator
        public GenerateFeedback(
                @JsonProperty("submissionContent") String submissionContent,
                @JsonProperty("categoryEvaluations") Map<String, GradingEvaluation> categoryEvaluations) {
            this.submissionContent = submissionContent;
            this.categoryEvaluations = categoryEvaluations;
        }
        
        public String getSubmissionContent() { return submissionContent; }
        public Map<String, GradingEvaluation> getCategoryEvaluations() { return categoryEvaluations; }
    }
    
    public static class FeedbackGenerated implements Message {
        private final String overallFeedback;
        
        @JsonCreator
        public FeedbackGenerated(@JsonProperty("overallFeedback") String overallFeedback) {
            this.overallFeedback = overallFeedback;
        }
        
        public String getOverallFeedback() { return overallFeedback; }
    }
    
    public static class FeedbackGenerationFailed implements Message {
        private final String error;
        
        @JsonCreator
        public FeedbackGenerationFailed(@JsonProperty("error") String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
    
    // Result writing messages
    public static class WriteResults implements Message {
        private final String studentId;
        private final String assignmentName;
        private final int totalScore;
        private final int maxPossibleScore;
        private final String grade;
        private final String overallFeedback;
        private final Map<String, GradingEvaluation> categoryEvaluations;
        
        @JsonCreator
        public WriteResults(
                @JsonProperty("studentId") String studentId,
                @JsonProperty("assignmentName") String assignmentName,
                @JsonProperty("totalScore") int totalScore,
                @JsonProperty("maxPossibleScore") int maxPossibleScore,
                @JsonProperty("grade") String grade,
                @JsonProperty("overallFeedback") String overallFeedback,
                @JsonProperty("categoryEvaluations") Map<String, GradingEvaluation> categoryEvaluations) {
            this.studentId = studentId;
            this.assignmentName = assignmentName;
            this.totalScore = totalScore;
            this.maxPossibleScore = maxPossibleScore;
            this.grade = grade;
            this.overallFeedback = overallFeedback;
            this.categoryEvaluations = categoryEvaluations;
        }
        
        public String getStudentId() { return studentId; }
        public String getAssignmentName() { return assignmentName; }
        public int getTotalScore() { return totalScore; }
        public int getMaxPossibleScore() { return maxPossibleScore; }
        public String getGrade() { return grade; }
        public String getOverallFeedback() { return overallFeedback; }
        public Map<String, GradingEvaluation> getCategoryEvaluations() { return categoryEvaluations; }
    }
    
    public static class ResultsWritten implements Message {
        private final String filePath;
        
        @JsonCreator
        public ResultsWritten(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
        }
        
        public String getFilePath() { return filePath; }
    }
    
    public static class ResultsWriteFailed implements Message {
        private final String error;
        
        @JsonCreator
        public ResultsWriteFailed(@JsonProperty("error") String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
    
    // Completion messages
    public static class GradingComplete implements Message {
        private final String studentId;
        private final String assignmentName;
        private final int totalScore;
        private final int maxPossibleScore;
        private final String grade;
        private final String overallFeedback;
        private final String detailedMCQFeedback;
        private final String detailedFeedback;
        
        @JsonCreator
        public GradingComplete(
                @JsonProperty("studentId") String studentId,
                @JsonProperty("assignmentName") String assignmentName,
                @JsonProperty("totalScore") int totalScore,
                @JsonProperty("maxPossibleScore") int maxPossibleScore,
                @JsonProperty("grade") String grade,
                @JsonProperty("overallFeedback") String overallFeedback,
                @JsonProperty("detailedMCQFeedback") String detailedMCQFeedback,
                @JsonProperty("detailedFeedback") String detailedFeedback) {
            this.studentId = studentId;
            this.assignmentName = assignmentName;
            this.totalScore = totalScore;
            this.maxPossibleScore = maxPossibleScore;
            this.grade = grade;
            this.overallFeedback = overallFeedback;
            this.detailedMCQFeedback = detailedMCQFeedback;
            this.detailedFeedback = detailedFeedback;
        }
        
        public String getStudentId() { return studentId; }
        public String getAssignmentName() { return assignmentName; }
        public int getTotalScore() { return totalScore; }
        public int getMaxPossibleScore() { return maxPossibleScore; }
        public String getGrade() { return grade; }
        public String getOverallFeedback() { return overallFeedback; }
        public String getDetailedMCQFeedback() { return detailedMCQFeedback; }
        public String getDetailedFeedback() { return detailedFeedback; }
    }
    
    public static class GradingFailed implements Message {
        private final String error;
        
        @JsonCreator
        public GradingFailed(@JsonProperty("error") String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
    
    // Logging messages
    public static class LogMessage implements Message {
        private final String level;
        private final String message;
        private final String source;
        
        @JsonCreator
        public LogMessage(
                @JsonProperty("level") String level,
                @JsonProperty("message") String message,
                @JsonProperty("source") String source) {
            this.level = level;
            this.message = message;
            this.source = source;
        }
        
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public String getSource() { return source; }
    }

    // Ask pattern messages - for request-response communication
    public static class WorkerHealthCheck implements Message {
        private final String requestId;
        private final ActorRef<Message> replyTo;
        
        @JsonCreator
        public WorkerHealthCheck(
                @JsonProperty("requestId") String requestId,
                @JsonProperty("replyTo") ActorRef<Message> replyTo) {
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
        
        public String getRequestId() { return requestId; }
        public ActorRef<Message> getReplyTo() { return replyTo; }
    }
    
    public static class WorkerHealthStatus implements Message {
        private final String requestId;
        private final String workerId;
        private final boolean healthy;
        private final int currentLoad;
        private final long responseTimeMs;
        
        @JsonCreator
        public WorkerHealthStatus(
                @JsonProperty("requestId") String requestId,
                @JsonProperty("workerId") String workerId,
                @JsonProperty("healthy") boolean healthy,
                @JsonProperty("currentLoad") int currentLoad,
                @JsonProperty("responseTimeMs") long responseTimeMs) {
            this.requestId = requestId;
            this.workerId = workerId;
            this.healthy = healthy;
            this.currentLoad = currentLoad;
            this.responseTimeMs = responseTimeMs;
        }
        
        public String getRequestId() { return requestId; }
        public String getWorkerId() { return workerId; }
        public boolean isHealthy() { return healthy; }
        public int getCurrentLoad() { return currentLoad; }
        public long getResponseTimeMs() { return responseTimeMs; }
    }

    public static class GradingCapacityCheck implements Message {
        private final ActorRef<Message> replyTo;
        
        @JsonCreator
        public GradingCapacityCheck(@JsonProperty("replyTo") ActorRef<Message> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<Message> getReplyTo() { return replyTo; }
    }
    
    public static class GradingCapacityResponse implements Message {
        private final int availableWorkers;
        private final int totalCapacity;
        private final int queuedJobs;
        
        @JsonCreator
        public GradingCapacityResponse(
                @JsonProperty("availableWorkers") int availableWorkers,
                @JsonProperty("totalCapacity") int totalCapacity,
                @JsonProperty("queuedJobs") int queuedJobs) {
            this.availableWorkers = availableWorkers;
            this.totalCapacity = totalCapacity;
            this.queuedJobs = queuedJobs;
        }
        
        public int getAvailableWorkers() { return availableWorkers; }
        public int getTotalCapacity() { return totalCapacity; }
        public int getQueuedJobs() { return queuedJobs; }
    }

    // Forward pattern messages - for delegation and routing
    public static class ForwardedSubmission implements Message {
        private final StudentSubmission originalSubmission;
        private final ActorRef<Message> originalSender;
        private final String routingInfo;
        
        @JsonCreator
        public ForwardedSubmission(
                @JsonProperty("originalSubmission") StudentSubmission originalSubmission,
                @JsonProperty("originalSender") ActorRef<Message> originalSender,
                @JsonProperty("routingInfo") String routingInfo) {
            this.originalSubmission = originalSubmission;
            this.originalSender = originalSender;
            this.routingInfo = routingInfo;
        }
        
        public StudentSubmission getOriginalSubmission() { return originalSubmission; }
        public ActorRef<Message> getOriginalSender() { return originalSender; }
        public String getRoutingInfo() { return routingInfo; }
    }

    public static class DelegateGrading implements Message {
        private final GradeCategory gradingTask;
        private final ActorRef<Message> originalRequester;
        private final String delegationReason;
        
        @JsonCreator
        public DelegateGrading(
                @JsonProperty("gradingTask") GradeCategory gradingTask,
                @JsonProperty("originalRequester") ActorRef<Message> originalRequester,
                @JsonProperty("delegationReason") String delegationReason) {
            this.gradingTask = gradingTask;
            this.originalRequester = originalRequester;
            this.delegationReason = delegationReason;
        }
        
        public GradeCategory getGradingTask() { return gradingTask; }
        public ActorRef<Message> getOriginalRequester() { return originalRequester; }
        public String getDelegationReason() { return delegationReason; }
    }

    public static class WorkerLoadBalanceRequest implements Message {
        private final GradeCategory gradingTask;
        private final ActorRef<Message> replyTo;
        
        @JsonCreator
        public WorkerLoadBalanceRequest(
                @JsonProperty("gradingTask") GradeCategory gradingTask,
                @JsonProperty("replyTo") ActorRef<Message> replyTo) {
            this.gradingTask = gradingTask;
            this.replyTo = replyTo;
        }
        
        public GradeCategory getGradingTask() { return gradingTask; }
        public ActorRef<Message> getReplyTo() { return replyTo; }
    }

    public static class SubmissionRoutingDecision implements Message {
        private final String targetCoordinator;
        private final String reason;
        
        @JsonCreator
        public SubmissionRoutingDecision(
                @JsonProperty("targetCoordinator") String targetCoordinator,
                @JsonProperty("reason") String reason) {
            this.targetCoordinator = targetCoordinator;
            this.reason = reason;
        }
        
        public String getTargetCoordinator() { return targetCoordinator; }
        public String getReason() { return reason; }
    }
} 