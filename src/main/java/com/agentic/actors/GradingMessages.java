package com.agentic.actors;

import com.agentic.models.RubricItem;
import com.agentic.models.Submission;
import com.agentic.models.GradingResult;
import com.agentic.utils.OpenAIClient.GradingEvaluation;

import java.util.List;
import java.util.Map;

/**
 * Message classes for Akka actor communication
 */
public class GradingMessages {

    /**
     * Base interface for all grading messages
     */
    public interface Message {}

    // Master Actor Messages
    public static final class StartGrading implements Message {
        private final String studentId;
        private final String assignmentName;
        
        public StartGrading(String studentId, String assignmentName) {
            this.studentId = studentId;
            this.assignmentName = assignmentName;
        }
        
        public String getStudentId() { return studentId; }
        public String getAssignmentName() { return assignmentName; }
    }
    
    public static final class GradingComplete implements Message {
        private final GradingResult result;
        
        public GradingComplete(GradingResult result) {
            this.result = result;
        }
        
        public GradingResult getResult() { return result; }
    }
    
    public static final class GradingFailed implements Message {
        private final String error;
        
        public GradingFailed(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }

    // Rubric Reader Messages
    public static final class LoadRubric implements Message {
        private final String filePath;
        
        public LoadRubric(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFilePath() { return filePath; }
    }
    
    public static final class RubricLoaded implements Message {
        private final List<RubricItem> rubricItems;
        
        public RubricLoaded(List<RubricItem> rubricItems) {
            this.rubricItems = rubricItems;
        }
        
        public List<RubricItem> getRubricItems() { return rubricItems; }
    }
    
    public static final class RubricLoadFailed implements Message {
        private final String error;
        
        public RubricLoadFailed(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }

    // Submission Reader Messages
    public static final class LoadSubmission implements Message {
        private final String filePath;
        
        public LoadSubmission(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFilePath() { return filePath; }
    }
    
    public static final class SubmissionLoaded implements Message {
        private final Submission submission;
        
        public SubmissionLoaded(Submission submission) {
            this.submission = submission;
        }
        
        public Submission getSubmission() { return submission; }
    }
    
    public static final class SubmissionLoadFailed implements Message {
        private final String error;
        
        public SubmissionLoadFailed(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }

    // Grader Actor Messages
    public static final class GradeCategory implements Message {
        private final String category;
        private final String submissionContent;
        private final RubricItem rubricItem;
        
        public GradeCategory(String category, String submissionContent, RubricItem rubricItem) {
            this.category = category;
            this.submissionContent = submissionContent;
            this.rubricItem = rubricItem;
        }
        
        public String getCategory() { return category; }
        public String getSubmissionContent() { return submissionContent; }
        public RubricItem getRubricItem() { return rubricItem; }
    }
    
    public static final class CategoryGraded implements Message {
        private final GradingEvaluation evaluation;
        
        public CategoryGraded(GradingEvaluation evaluation) {
            this.evaluation = evaluation;
        }
        
        public GradingEvaluation getEvaluation() { return evaluation; }
    }
    
    public static final class CategoryGradingFailed implements Message {
        private final String category;
        private final String error;
        
        public CategoryGradingFailed(String category, String error) {
            this.category = category;
            this.error = error;
        }
        
        public String getCategory() { return category; }
        public String getError() { return error; }
    }

    // Feedback Actor Messages
    public static final class GenerateFeedback implements Message {
        private final String submissionContent;
        private final Map<String, GradingEvaluation> categoryEvaluations;
        
        public GenerateFeedback(String submissionContent, Map<String, GradingEvaluation> categoryEvaluations) {
            this.submissionContent = submissionContent;
            this.categoryEvaluations = categoryEvaluations;
        }
        
        public String getSubmissionContent() { return submissionContent; }
        public Map<String, GradingEvaluation> getCategoryEvaluations() { return categoryEvaluations; }
    }
    
    public static final class FeedbackGenerated implements Message {
        private final String overallFeedback;
        
        public FeedbackGenerated(String overallFeedback) {
            this.overallFeedback = overallFeedback;
        }
        
        public String getOverallFeedback() { return overallFeedback; }
    }
    
    public static final class FeedbackGenerationFailed implements Message {
        private final String error;
        
        public FeedbackGenerationFailed(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }

    // Result Writer Messages
    public static final class WriteResults implements Message {
        private final List<GradingResult> results;
        private final String filePath;
        
        public WriteResults(List<GradingResult> results, String filePath) {
            this.results = results;
            this.filePath = filePath;
        }
        
        public List<GradingResult> getResults() { return results; }
        public String getFilePath() { return filePath; }
    }
    
    public static final class ResultsWritten implements Message {
        private final String filePath;
        
        public ResultsWritten(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFilePath() { return filePath; }
    }
    
    public static final class ResultsWriteFailed implements Message {
        private final String error;
        
        public ResultsWriteFailed(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }

    // Cluster Messages
    public static final class ClusterMemberUp implements Message {
        private final String memberAddress;
        
        public ClusterMemberUp(String memberAddress) {
            this.memberAddress = memberAddress;
        }
        
        public String getMemberAddress() { return memberAddress; }
    }
    
    public static final class ClusterMemberDown implements Message {
        private final String memberAddress;
        
        public ClusterMemberDown(String memberAddress) {
            this.memberAddress = memberAddress;
        }
        
        public String getMemberAddress() { return memberAddress; }
    }
    
    public static final class WorkDistributed implements Message {
        private final String category;
        private final String workerAddress;
        
        public WorkDistributed(String category, String workerAddress) {
            this.category = category;
            this.workerAddress = workerAddress;
        }
        
        public String getCategory() { return category; }
        public String getWorkerAddress() { return workerAddress; }
    }
    
    public static final class WorkCompleted implements Message {
        private final String category;
        private final GradingEvaluation evaluation;
        
        public WorkCompleted(String category, GradingEvaluation evaluation) {
            this.category = category;
            this.evaluation = evaluation;
        }
        
        public String getCategory() { return category; }
        public GradingEvaluation getEvaluation() { return evaluation; }
    }
} 