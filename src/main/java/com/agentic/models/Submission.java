package com.agentic.models;

import java.time.LocalDateTime;

/**
 * Represents a student submission with content and metadata
 */
public class Submission {
    private String studentId;
    private String assignmentName;
    private String content;
    private LocalDateTime submittedAt;
    private String fileName;

    public Submission() {}

    public Submission(String studentId, String assignmentName, String content) {
        this.studentId = studentId;
        this.assignmentName = assignmentName;
        this.content = content;
        this.submittedAt = LocalDateTime.now();
    }

    public Submission(String studentId, String assignmentName, String content, String fileName) {
        this(studentId, assignmentName, content);
        this.fileName = fileName;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Get the word count of the submission content
     */
    public int getWordCount() {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    /**
     * Get the character count of the submission content
     */
    public int getCharacterCount() {
        return content != null ? content.length() : 0;
    }

    @Override
    public String toString() {
        return "Submission{" +
                "studentId='" + studentId + '\'' +
                ", assignmentName='" + assignmentName + '\'' +
                ", wordCount=" + getWordCount() +
                ", characterCount=" + getCharacterCount() +
                ", submittedAt=" + submittedAt +
                ", fileName='" + fileName + '\'' +
                '}';
    }
} 