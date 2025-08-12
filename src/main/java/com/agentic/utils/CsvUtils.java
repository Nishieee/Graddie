package com.agentic.utils;

import com.agentic.models.RubricItem;
import com.agentic.models.GradingResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class for CSV file operations
 */
public class CsvUtils {
    private static final Logger logger = LoggerFactory.getLogger(CsvUtils.class);

    /**
     * Read rubric items from CSV file
     */
    public static List<RubricItem> readRubricFromCsv(String filePath) throws IOException {
        List<RubricItem> rubricItems = new ArrayList<>();
        
        try (Reader reader = new FileReader(filePath, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            
            for (CSVRecord record : csvParser) {
                try {
                    RubricItem item = new RubricItem();
                    item.setCategory(record.get("Category"));
                    item.setDescription(record.get("Description"));
                    item.setMaxPoints(Integer.parseInt(record.get("MaxPoints")));
                    
                    // Parse score bands
                    item.setExcellent(parseScoreBand(record, "Excellent"));
                    item.setGood(parseScoreBand(record, "Good"));
                    item.setFair(parseScoreBand(record, "Fair"));
                    item.setNeedsImprovement(parseScoreBand(record, "NeedsImprovement"));
                    
                    rubricItems.add(item);
                    logger.debug("Loaded rubric item: {}", item.getCategory());
                } catch (Exception e) {
                    logger.error("Error parsing rubric record: {}", record, e);
                }
            }
        }
        

        return rubricItems;
    }

    /**
     * Parse score band from CSV record
     */
    private static RubricItem.ScoreBand parseScoreBand(CSVRecord record, String bandName) {
        String description = record.get(bandName + "Description");
        String points = record.get(bandName + "Points");
        
        if (description == null || points == null || description.trim().isEmpty()) {
            return null;
        }
        
        try {
            int pointValue = Integer.parseInt(points.trim());
            return new RubricItem.ScoreBand(description.trim(), 0, pointValue);
        } catch (NumberFormatException e) {
            logger.warn("Invalid point value for {}: {}", bandName, points);
            return null;
        }
    }

    /**
     * Write grading results to CSV file (appends new results)
     */
    public static void writeGradingResultsToCsv(String filePath, List<GradingResult> results) throws IOException {
        File file = new File(filePath);
        boolean fileExists = file.exists() && file.length() > 0;
        
        try (Writer writer = new FileWriter(filePath, StandardCharsets.UTF_8, true); // true = append mode
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // Determine ordered category headers once
            List<String> orderedCategories = new ArrayList<>();
            if (!results.isEmpty() && results.get(0).getCategoryScores() != null) {
                orderedCategories.addAll(results.get(0).getCategoryScores().keySet());
                java.util.Collections.sort(orderedCategories);
            }

            // Only write header if file doesn't exist or is empty
            if (!fileExists) {
                List<String> header = new ArrayList<>(java.util.List.of(
                    "StudentID", "AssignmentName", "TotalScore", "MaxPossibleScore",
                    "Percentage", "Grade", "GradedAt", "OverallFeedback"
                ));
                for (String category : orderedCategories) {
                    header.add(category + "_Score");
                    header.add(category + "_MaxPoints");
                    header.add(category + "_Percentage");
                    header.add(category + "_ScoreBand");
                    header.add(category + "_Feedback");
                }
                csvPrinter.printRecord(header);
                logger.info("Created new CSV file with header: {}", filePath);
            }

            // Write data rows (append to existing file)
            for (GradingResult result : results) {
                List<String> row = new ArrayList<>();
                row.add(result.getStudentId());
                row.add(result.getAssignmentName());
                row.add(String.valueOf(result.getTotalScore()));
                row.add(String.valueOf(result.getMaxPossibleScore()));
                row.add(String.format("%.1f", result.getPercentage()));
                row.add(result.getGrade());
                row.add(result.getGradedAt().toString());
                row.add(result.getOverallFeedback() != null ? result.getOverallFeedback().replace("\n", " ") : "");

                // Add category scores in the same order as header
                for (String category : orderedCategories) {
                    GradingResult.CategoryScore cs = result.getCategoryScores() != null ? result.getCategoryScores().get(category) : null;
                    if (cs != null) {
                        row.add(String.valueOf(cs.getScore()));
                        row.add(String.valueOf(cs.getMaxPoints()));
                        row.add(String.format("%.1f", cs.getPercentage()));
                        row.add(cs.getScoreBand());
                        row.add(cs.getFeedback() != null ? cs.getFeedback().replace("\n", " ") : "");
                    } else {
                        // Fill blanks if missing
                        row.add("");
                        row.add("");
                        row.add("");
                        row.add("");
                        row.add("");
                    }
                }
                
                csvPrinter.printRecord(row);
            }
        }
        
        logger.info("Appended {} grading results to {}", results.size(), filePath);
    }

    /**
     * Read submission content from text file
     */
    public static String readSubmissionFromFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        logger.info("Read submission content from {} ({} characters)", filePath, content.length());
        return content.toString();
    }

    /**
     * Create sample rubric CSV file for testing
     */
    public static void createSampleRubricCsv(String filePath) throws IOException {
        try (Writer writer = new FileWriter(filePath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            
            // Write header
            csvPrinter.printRecord(
                "Category", "Description", "MaxPoints",
                "ExcellentDescription", "ExcellentPoints",
                "GoodDescription", "GoodPoints",
                "FairDescription", "FairPoints",
                "NeedsImprovementDescription", "NeedsImprovementPoints"
            );
            
            // Write sample rubric items
            csvPrinter.printRecord(
                "Content Quality", "Depth and relevance of content", "30",
                "Exceptional depth and insight", "30",
                "Good depth with minor gaps", "25",
                "Adequate coverage with some gaps", "20",
                "Limited depth and relevance", "15"
            );
            
            csvPrinter.printRecord(
                "Organization", "Logical structure and flow", "30",
                "Excellent organization and flow", "30",
                "Good organization with minor issues", "25",
                "Adequate organization", "20",
                "Poor organization and flow", "15"
            );
            
            csvPrinter.printRecord(
                "Critical Thinking", "Analysis and evaluation", "30",
                "Exceptional critical analysis", "30",
                "Good analysis with minor gaps", "25",
                "Adequate analysis", "20",
                "Limited critical thinking", "15"
            );
            
            csvPrinter.printRecord(
                "Mechanics", "Grammar, spelling, formatting", "10",
                "Excellent mechanics", "10",
                "Good mechanics with minor errors", "8",
                "Adequate mechanics", "6",
                "Poor mechanics", "4"
            );
        }
        
        logger.info("Created sample rubric CSV at {}", filePath);
    }
} 