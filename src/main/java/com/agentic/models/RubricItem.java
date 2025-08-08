package com.agentic.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a rubric item with scoring criteria and bands
 */
public class RubricItem {
    private String category;
    private String description;
    private int maxPoints;
    private ScoreBand excellent;
    private ScoreBand good;
    private ScoreBand fair;
    private ScoreBand needsImprovement;

    public RubricItem() {}

    public RubricItem(String category, String description, int maxPoints) {
        this.category = category;
        this.description = description;
        this.maxPoints = maxPoints;
    }

    // Getters and Setters
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public ScoreBand getExcellent() {
        return excellent;
    }

    public void setExcellent(ScoreBand excellent) {
        this.excellent = excellent;
    }

    public ScoreBand getGood() {
        return good;
    }

    public void setGood(ScoreBand good) {
        this.good = good;
    }

    public ScoreBand getFair() {
        return fair;
    }

    public void setFair(ScoreBand fair) {
        this.fair = fair;
    }

    public ScoreBand getNeedsImprovement() {
        return needsImprovement;
    }

    public void setNeedsImprovement(ScoreBand needsImprovement) {
        this.needsImprovement = needsImprovement;
    }

    @Override
    public String toString() {
        return "RubricItem{" +
                "category='" + category + '\'' +
                ", description='" + description + '\'' +
                ", maxPoints=" + maxPoints +
                '}';
    }

    /**
     * Represents a scoring band with description and point range
     */
    public static class ScoreBand {
        private String description;
        private int minPoints;
        private int maxPoints;

        public ScoreBand() {}

        public ScoreBand(String description, int minPoints, int maxPoints) {
            this.description = description;
            this.minPoints = minPoints;
            this.maxPoints = maxPoints;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getMinPoints() {
            return minPoints;
        }

        public void setMinPoints(int minPoints) {
            this.minPoints = minPoints;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public void setMaxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
        }

        @Override
        public String toString() {
            return "ScoreBand{" +
                    "description='" + description + '\'' +
                    ", minPoints=" + minPoints +
                    ", maxPoints=" + maxPoints +
                    '}';
        }
    }
} 