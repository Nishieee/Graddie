package com.agentic.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the complete grading result for a submission
 */
public class GradingResult {
    private String studentId;
    private String assignmentName;
    private LocalDateTime gradedAt;
    private Map<String, CategoryScore> categoryScores;
    private String overallFeedback;
    private int totalScore;
    private int maxPossibleScore;
    private String grade;
    private List<String> strengths;
    private List<String> areasForImprovement;

    public GradingResult() {
        this.gradedAt = LocalDateTime.now();
    }

    public GradingResult(String studentId, String assignmentName) {
        this();
        this.studentId = studentId;
        this.assignmentName = assignmentName;
    }

    // Getters and Setters
    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getAssignmentName() {
        return assignmentName;
    }

    public void setAssignmentName(String assignmentName) {
        this.assignmentName = assignmentName;
    }

    public LocalDateTime getGradedAt() {
        return gradedAt;
    }

    public void setGradedAt(LocalDateTime gradedAt) {
        this.gradedAt = gradedAt;
    }

    public Map<String, CategoryScore> getCategoryScores() {
        return categoryScores;
    }

    public void setCategoryScores(Map<String, CategoryScore> categoryScores) {
        this.categoryScores = categoryScores;
    }

    public String getOverallFeedback() {
        return overallFeedback;
    }

    public void setOverallFeedback(String overallFeedback) {
        this.overallFeedback = overallFeedback;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public int getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public void setMaxPossibleScore(int maxPossibleScore) {
        this.maxPossibleScore = maxPossibleScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getAreasForImprovement() {
        return areasForImprovement;
    }

    public void setAreasForImprovement(List<String> areasForImprovement) {
        this.areasForImprovement = areasForImprovement;
    }

    /**
     * Calculate the percentage score
     */
    public double getPercentage() {
        if (maxPossibleScore == 0) return 0.0;
        return (double) totalScore / maxPossibleScore * 100.0;
    }

    /**
     * Calculate letter grade based on percentage
     */
    public String calculateLetterGrade() {
        double percentage = getPercentage();
        if (percentage >= 93) return "A";
        else if (percentage >= 90) return "A-";
        else if (percentage >= 87) return "B+";
        else if (percentage >= 83) return "B";
        else if (percentage >= 80) return "B-";
        else if (percentage >= 77) return "C+";
        else if (percentage >= 73) return "C";
        else if (percentage >= 70) return "C-";
        else if (percentage >= 67) return "D+";
        else if (percentage >= 63) return "D";
        else if (percentage >= 60) return "D-";
        else return "F";
    }

    @Override
    public String toString() {
        return "GradingResult{" +
                "studentId='" + studentId + '\'' +
                ", assignmentName='" + assignmentName + '\'' +
                ", totalScore=" + totalScore +
                ", maxPossibleScore=" + maxPossibleScore +
                ", percentage=" + String.format("%.1f%%", getPercentage()) +
                ", grade='" + grade + '\'' +
                ", gradedAt=" + gradedAt +
                '}';
    }

    /**
     * Represents a score for a specific category
     */
    public static class CategoryScore {
        private String category;
        private int score;
        private int maxPoints;
        private String feedback;
        private String scoreBand; // Excellent, Good, Fair, Needs Improvement

        public CategoryScore() {}

        public CategoryScore(String category, int score, int maxPoints, String feedback, String scoreBand) {
            this.category = category;
            this.score = score;
            this.maxPoints = maxPoints;
            this.feedback = feedback;
            this.scoreBand = scoreBand;
        }

        // Getters and Setters
        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public void setMaxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }

        public String getScoreBand() {
            return scoreBand;
        }

        public void setScoreBand(String scoreBand) {
            this.scoreBand = scoreBand;
        }

        /**
         * Calculate the percentage for this category
         */
        public double getPercentage() {
            if (maxPoints == 0) return 0.0;
            return (double) score / maxPoints * 100.0;
        }

        @Override
        public String toString() {
            return "CategoryScore{" +
                    "category='" + category + '\'' +
                    ", score=" + score +
                    ", maxPoints=" + maxPoints +
                    ", percentage=" + String.format("%.1f%%", getPercentage()) +
                    ", scoreBand='" + scoreBand + '\'' +
                    '}';
        }
    }
} 