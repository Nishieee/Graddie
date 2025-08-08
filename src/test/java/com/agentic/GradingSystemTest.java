package com.agentic;

import com.agentic.models.GradingResult;
import com.agentic.models.RubricItem;
import com.agentic.models.Submission;
import com.agentic.utils.CsvUtils;
import com.agentic.utils.TextPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for the grading system
 */
public class GradingSystemTest {

    @TempDir
    Path tempDir;

    private String sampleSubmission;
    private List<RubricItem> rubricItems;

    @BeforeEach
    void setUp() throws IOException {
        // Create sample submission
        sampleSubmission = """
            The Impact of Artificial Intelligence on Modern Education
            
            Introduction
            Artificial Intelligence (AI) has emerged as a transformative force in modern education, 
            fundamentally changing how students learn and how educators teach. This essay explores 
            the multifaceted impact of AI on educational systems, examining both its benefits and 
            potential challenges.
            
            Content Quality and Depth
            AI technologies have revolutionized content delivery through personalized learning 
            platforms that adapt to individual student needs. These systems analyze student 
            performance data to provide customized educational experiences, ensuring that each 
            learner receives instruction tailored to their unique learning style and pace. 
            Furthermore, AI-powered tools can generate diverse learning materials, from interactive 
            simulations to adaptive quizzes, enhancing the overall quality of educational content.
            
            However, the implementation of AI in education raises important questions about 
            data privacy and the potential for algorithmic bias. Educational institutions must 
            carefully consider these ethical implications while harnessing AI's capabilities.
            
            Organization and Structure
            The essay demonstrates clear organization with a logical flow from introduction 
            to conclusion. Each paragraph builds upon the previous one, creating a coherent 
            argument about AI's role in education. The use of transitional phrases and 
            topic sentences helps guide the reader through the complex subject matter.
            
            Critical Thinking and Analysis
            The analysis goes beyond surface-level observations to explore deeper implications. 
            The author considers multiple perspectives, acknowledging both the benefits and 
            potential drawbacks of AI in education. This balanced approach demonstrates 
            sophisticated critical thinking skills and the ability to evaluate complex issues 
            from multiple angles.
            
            Conclusion
            While AI presents unprecedented opportunities for educational enhancement, its 
            successful integration requires careful consideration of ethical, practical, and 
            pedagogical factors. The future of education lies in finding the right balance 
            between technological innovation and human-centered learning principles.
            """;

        // Create sample rubric CSV
        String rubricCsvPath = tempDir.resolve("test_rubric.csv").toString();
        CsvUtils.createSampleRubricCsv(rubricCsvPath);
        rubricItems = CsvUtils.readRubricFromCsv(rubricCsvPath);
    }

    @Test
    void testRubricLoading() {
        assertNotNull(rubricItems);
        assertEquals(4, rubricItems.size());
        
        // Check first rubric item
        RubricItem firstItem = rubricItems.get(0);
        assertEquals("Content Quality", firstItem.getCategory());
        assertEquals(30, firstItem.getMaxPoints());
        assertNotNull(firstItem.getExcellent());
        assertNotNull(firstItem.getGood());
        assertNotNull(firstItem.getFair());
        assertNotNull(firstItem.getNeedsImprovement());
    }

    @Test
    void testSubmissionCreation() {
        Submission submission = new Submission("TEST001", "Essay Assignment", sampleSubmission);
        
        assertEquals("TEST001", submission.getStudentId());
        assertEquals("Essay Assignment", submission.getAssignmentName());
        assertEquals(sampleSubmission, submission.getContent());
        assertTrue(submission.getWordCount() > 0);
        assertTrue(submission.getCharacterCount() > 0);
    }

    @Test
    void testTextPreprocessing() {
        String processed = TextPreprocessor.preprocessText(sampleSubmission);
        
        assertNotNull(processed);
        assertFalse(processed.isEmpty());
        assertTrue(processed.length() < sampleSubmission.length()); // Should be shorter after preprocessing
        
        // Check that common stopwords are removed
        assertFalse(processed.contains(" the "));
        assertFalse(processed.contains(" and "));
        assertFalse(processed.contains(" of "));
    }

    @Test
    void testWordCountStats() {
        var stats = TextPreprocessor.getWordCountStats(sampleSubmission);
        
        assertNotNull(stats);
        assertTrue(stats.get("total_words") > 0);
        assertTrue(stats.get("unique_words") > 0);
        assertTrue(stats.get("characters") > 0);
        assertTrue(stats.get("sentences") > 0);
    }

    @Test
    void testKeyTermsExtraction() {
        List<String> keyTerms = TextPreprocessor.extractKeyTerms(sampleSubmission, 2);
        
        assertNotNull(keyTerms);
        assertFalse(keyTerms.isEmpty());
        assertTrue(keyTerms.size() <= 20); // Should be limited to top 20 terms
    }

    @Test
    void testGradingResultCreation() {
        GradingResult result = new GradingResult("TEST001", "Essay Assignment");
        
        assertEquals("TEST001", result.getStudentId());
        assertEquals("Essay Assignment", result.getAssignmentName());
        assertNotNull(result.getGradedAt());
        assertEquals(0, result.getTotalScore());
        assertEquals(0, result.getMaxPossibleScore());
        assertEquals(0.0, result.getPercentage());
    }

    @Test
    void testGradingResultCalculations() {
        GradingResult result = new GradingResult("TEST001", "Essay Assignment");
        result.setTotalScore(85);
        result.setMaxPossibleScore(100);
        
        assertEquals(85.0, result.getPercentage());
        assertEquals("B", result.calculateLetterGrade());
    }

    @Test
    void testCsvUtilsSampleCreation() throws IOException {
        String csvPath = tempDir.resolve("sample_rubric.csv").toString();
        CsvUtils.createSampleRubricCsv(csvPath);
        
        List<RubricItem> items = CsvUtils.readRubricFromCsv(csvPath);
        assertEquals(4, items.size());
        
        // Verify all categories are present
        List<String> categories = items.stream()
            .map(RubricItem::getCategory)
            .toList();
        
        assertTrue(categories.contains("Content Quality"));
        assertTrue(categories.contains("Organization"));
        assertTrue(categories.contains("Critical Thinking"));
        assertTrue(categories.contains("Mechanics"));
    }
} 