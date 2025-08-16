package com.agentic.utils;

import com.agentic.models.RubricItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

/**
 * HTTP client for OpenAI API integration
 */
public class OpenAIClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    
    public OpenAIClient(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-3.5-turbo");
    }
    
    public OpenAIClient(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, "gpt-3.5-turbo");
    }

    public OpenAIClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Evaluate a submission against a rubric category using OpenAI (synchronous version)
     */
    public GradingEvaluation evaluateSubmissionSync(
            String submissionContent, 
            String rubricCategory, 
            String rubricDescription, 
            int maxPoints,
            Map<String, RubricItem.ScoreBand> scoreBands,
            com.agentic.actors.GraddieMessages.QuestionType questionType,
            String correctAnswers) {
        
        try {
            String prompt = buildEvaluationPrompt(submissionContent, rubricCategory, rubricDescription, maxPoints, scoreBands, questionType, correctAnswers);
            String response = makeOpenAIRequest(prompt);
            return parseEvaluationResponse(response, rubricCategory, maxPoints, questionType);
        } catch (Exception e) {
            logger.error("Error evaluating submission for category: {}", rubricCategory, e);
            return new GradingEvaluation(rubricCategory, 0, maxPoints, "Error during evaluation", "Needs Improvement");
        }
    }

    /**
     * Evaluate a submission against a rubric category using OpenAI
     */
    public CompletableFuture<GradingEvaluation> evaluateSubmission(
            String submissionContent, 
            String rubricCategory, 
            String rubricDescription, 
            int maxPoints,
            Map<String, RubricItem.ScoreBand> scoreBands) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildEvaluationPrompt(submissionContent, rubricCategory, rubricDescription, maxPoints, scoreBands, 
                    com.agentic.actors.GraddieMessages.QuestionType.ESSAY, null);
                String response = makeOpenAIRequest(prompt);
                return parseEvaluationResponse(response, rubricCategory, maxPoints, 
                    com.agentic.actors.GraddieMessages.QuestionType.ESSAY);
            } catch (Exception e) {
                logger.error("Error evaluating submission for category: {}", rubricCategory, e);
                return new GradingEvaluation(rubricCategory, 0, maxPoints, "Error during evaluation", "Needs Improvement");
            }
        });
    }

    /**
     * Build the evaluation prompt for OpenAI
     */
    private String buildEvaluationPrompt(
            String submissionContent, 
            String rubricCategory, 
            String rubricDescription, 
            int maxPoints,
            Map<String, RubricItem.ScoreBand> scoreBands,
            com.agentic.actors.GraddieMessages.QuestionType questionType,
            String correctAnswers) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a concise academic evaluator. Follow the rubric exactly; do not invent scores.\n");
        prompt.append("Only award points clearly supported by evidence in the submission.\n");
        prompt.append("Return brief feedback (max 3 sentences; ≤ 350 characters).\n\n");
        prompt.append("CATEGORY: ").append(rubricCategory).append("\n");
        prompt.append("DESCRIPTION: ").append(rubricDescription).append("\n");
        prompt.append("MAXIMUM POINTS: ").append(maxPoints).append("\n");
        prompt.append("QUESTION TYPE: ").append(questionType).append("\n\n");
        
        if (questionType == com.agentic.actors.GraddieMessages.QuestionType.MCQ || 
            questionType == com.agentic.actors.GraddieMessages.QuestionType.SHORT_ANSWER) {
            prompt.append("CORRECT ANSWERS:\n");
            prompt.append(correctAnswers).append("\n\n");
            
            // For MCQ, also include the full question context to help LLM understand A, B, C, D
            if (questionType == com.agentic.actors.GraddieMessages.QuestionType.MCQ) {
                prompt.append("IMPORTANT: The student submission contains the full questions with A, B, C, D options. ");
                prompt.append("Compare the student's selected answers (A, B, C, or D) with the correct answers provided above.\n\n");
            }
        }
        
        prompt.append("SCORING BANDS:\n");
        for (Map.Entry<String, RubricItem.ScoreBand> entry : scoreBands.entrySet()) {
            RubricItem.ScoreBand band = entry.getValue();
            prompt.append("- ").append(entry.getKey()).append(" (").append(band.getMaxPoints()).append(" points): ")
                  .append(band.getDescription()).append("\n");
        }
        
        prompt.append("\nSTUDENT SUBMISSION:\n");
        prompt.append(submissionContent).append("\n\n");
        
        prompt.append("INSTRUCTIONS:\n");
        
        if (questionType == com.agentic.actors.GraddieMessages.QuestionType.MCQ) {
            prompt.append("STRICT EVALUATION RULES:\n");
            prompt.append("1. BLANK/EMPTY ANSWERS = 0 POINTS - No exceptions!\n");
            prompt.append("2. If answer line exists but is empty (e.g., 'Answer: ' with nothing after), that's BLANK = 0 points\n");
            prompt.append("3. Only award points for actual, substantive answers\n");
            prompt.append("4. For MCQ: Look for 'Answer: X' where X is A, B, C, or D\n");
            prompt.append("5. For Short Answer: Look for actual content after 'Answer:'\n");
            prompt.append("6. Score = (correct_answers / total_questions) * max_points\n");
            prompt.append("7. Be HARSH on incomplete or missing answers\n");
            prompt.append("8. Missing answers should be explicitly called out as 'No answer provided'\n\n");
            
            prompt.append("EXAMPLE PARSING:\n");
            prompt.append("If submission contains:\n");
            prompt.append("'What is 5 + 3?\nA. 6\nB. 8\nC. 10\nAnswer: B'\n");
            prompt.append("Then student chose B (NOT A, even though A. 6 appears in the question)\n\n");
            
            prompt.append("FEEDBACK REQUIREMENTS - Be VERY detailed and educational:\n");
            prompt.append("- For CORRECT answers: Praise the student and explain WHY it's correct\n");
            prompt.append("- For INCORRECT answers: Start with 'This question was marked incorrectly' then explain:\n");
            prompt.append("  • What the student chose and what it represents\n");
            prompt.append("  • What the correct answer is and what it represents\n");
            prompt.append("  • Step-by-step explanation of how to solve it correctly\n");
            prompt.append("  • Tips to avoid this mistake in the future\n");
            prompt.append("- Use encouraging but educational language\n");
            prompt.append("- Give concrete examples and step-by-step reasoning\n");
            prompt.append("- Make each explanation 2-3 sentences minimum\n\n");
            
            prompt.append("Return your response in this JSON format:\n");
            prompt.append("{\n");
            prompt.append("  \"score\": <number of points awarded>,\n");
            prompt.append("  \"scoreBand\": \"<Excellent|Good|Fair|Needs Improvement>\",\n");
            prompt.append("  \"feedback\": \"<overall feedback>\",\n");
            prompt.append("  \"mcqDetails\": {\n");
            prompt.append("    \"totalQuestions\": <number>,\n");
            prompt.append("    \"correctAnswers\": <number>,\n");
            prompt.append("    \"questionFeedback\": [\n");
            prompt.append("      {\n");
            prompt.append("        \"question\": 1,\n");
            prompt.append("        \"correct\": true/false,\n");
            prompt.append("        \"studentAnswer\": \"A\",\n");
            prompt.append("        \"correctAnswer\": \"B\",\n");
            prompt.append("        \"explanation\": \"<brief explanation of why the correct answer is right and why student's answer is wrong>\"\n");
            prompt.append("      }\n");
            prompt.append("    ]\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
            
        } else if (questionType == com.agentic.actors.GraddieMessages.QuestionType.SHORT_ANSWER) {
            prompt.append("STRICT SHORT ANSWER EVALUATION RULES:\n");
            prompt.append("1. BLANK/EMPTY ANSWERS = 0.0 POINTS - No exceptions!\n");
            prompt.append("2. If 'Answer:' line is missing or contains only whitespace, that's 0.0\n");
            prompt.append("3. STRICT SCORING GUIDE:\n");
            prompt.append("   - 1.0 = Perfect, complete, correct answer\n");
            prompt.append("   - 0.8 = Very good answer, mostly correct\n");
            prompt.append("   - 0.6 = Good answer, shows understanding\n");
            prompt.append("   - 0.4 = Partial answer, some understanding\n");
            prompt.append("   - 0.2 = Minimal/incorrect answer\n");
            prompt.append("   - 0.0 = BLANK, empty, or completely wrong\n");
            prompt.append("4. Be HARSH on incomplete answers - missing answers get 0.0\n");
            prompt.append("5. Only award points for actual, substantive content\n");
            prompt.append("6. Count unanswered questions as failures in your scoring\n\n");
            
            prompt.append("Return your response in this JSON format:\n");
            prompt.append("{\n");
            prompt.append("  \"score\": <decimal score between 0 and 1>,\n");
            prompt.append("  \"outOf\": 1,\n");
            prompt.append("  \"scoreBand\": \"<Excellent|Good|Fair|Needs Improvement>\",\n");
            prompt.append("  \"feedback\": \"<concise corrective feedback explaining what was right/wrong and how to improve>\",\n");
            prompt.append("  \"detailedFeedback\": {\n");
            prompt.append("    \"overallAssessment\": \"<overall right/wrong assessment>\",\n");
            prompt.append("    \"keyPoints\": [\n");
            prompt.append("      {\n");
            prompt.append("        \"point\": \"<specific point or concept>\",\n");
            prompt.append("        \"correct\": true/false,\n");
            prompt.append("        \"explanation\": \"<why this point is right or wrong>\",\n");
            prompt.append("        \"suggestion\": \"<how to improve this point>\"\n");
            prompt.append("      }\n");
            prompt.append("    ]\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
            prompt.append("CRITICAL REMINDER: If you say the student 'correctly identified' or 'shows understanding' in your feedback, \n");
            prompt.append("you MUST give a score of 0.6 or higher. Don't contradict yourself!\n\n");
            
        } else { // ESSAY
            prompt.append("ESSAY EVALUATION (be brief, actionable, and PERSONALIZED):\n");
            prompt.append("- Address the student directly using 'you'.\n");
            prompt.append("- Include exactly 1 short quote (≤ 8 words) OR a paraphrase from the student's submission to prove your point.\n");
            prompt.append("- Each point must be justified with 'because ...' referencing their content.\n");
            prompt.append("- Avoid generic phrases like 'excellent', 'great work' without specifics.\n");
            prompt.append("- Include one concrete next step tailored to this student's work.\n");
            prompt.append("- Keep feedback concise (≤ 350 characters).\n");
            prompt.append("Return your response in this JSON format (no extra text):\n");
            prompt.append("{\n");
            prompt.append("  \"score\": <number of points awarded>,\n");
            prompt.append("  \"outOf\": ").append(maxPoints).append(",\n");
            prompt.append("  \"scoreBand\": \"<Excellent|Good|Fair|Needs Improvement>\",\n");
            prompt.append("  \"feedback\": \"<max 3 short sentences (≤ 350 chars), speak to 'you', use one short quote or paraphrase from their text, include one tailored next step>\"\n");
            prompt.append("}\n\n");
        }
        
        prompt.append("FINAL REMINDER:\n");
        prompt.append("- Follow the rubric exactly - do not make up your own scoring criteria\n");
        prompt.append("- Be evidence-based - only award points you can justify from the submission\n");
        prompt.append("- Double-check your math and scoring logic\n");
        prompt.append("- Return ONLY valid JSON - no extra text\n\n");
        prompt.append("Provide your strict, evidence-based evaluation:");
        
        return prompt.toString();
    }

    /**
     * Make HTTP request to OpenAI API
     */
    private String makeOpenAIRequest(String prompt) throws IOException {
        // Check if we're using a mock key
        if (apiKey.equals("mock-key-for-testing") || apiKey.equals("mock-key")) {
            return generateMockResponse(prompt);
        }
        
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.0,
            "max_tokens", 1500
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API request failed: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                return (String) message.get("content");
            }
            
            throw new IOException("Invalid response format from OpenAI API");
        }
    }
    


    /**
     * Generate mock responses for testing without API key
     */
    private String generateMockResponse(String prompt) {
        // Only use mock if absolutely no API key is available
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return """
                {
                    "score": 0,
                    "scoreBand": "Needs Improvement",
                    "feedback": "No OpenAI API key available. Please set OPENAI_API_KEY environment variable for real AI-powered feedback."
                }
                """;
        }
        
        // If we have an API key but it's marked as mock, throw an error
        throw new RuntimeException("Invalid API key configuration. Please check your OPENAI_API_KEY environment variable.");
    }

    /**
     * Parse the evaluation response from OpenAI
     */
    private GradingEvaluation parseEvaluationResponse(String response, String category, int maxPoints, 
            com.agentic.actors.GraddieMessages.QuestionType questionType) {
        try {
            // Extract JSON from response (in case there's extra text)
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}') + 1;
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonResponse = response.substring(jsonStart, jsonEnd);
                Map<String, Object> evaluation = objectMapper.readValue(jsonResponse, Map.class);
                
                double rawScore = ((Number) evaluation.get("score")).doubleValue();
                int score;
                
                if (questionType == com.agentic.actors.GraddieMessages.QuestionType.SHORT_ANSWER) {
                    // For Short Answer: AI returns 0.0-1.0, multiply by maxPoints
                    score = (int) Math.round(rawScore * maxPoints);
                } else {
                    // For MCQ and Essay: AI returns actual point value, use as-is
                    score = (int) Math.round(rawScore);
                }
                
                String scoreBand = (String) evaluation.get("scoreBand");
                String feedback = (String) evaluation.get("feedback");
                
                // Handle detailed feedback for short answers
                String detailedFeedback = null;
                if (questionType == com.agentic.actors.GraddieMessages.QuestionType.SHORT_ANSWER) {
                    Object detailedFeedbackObj = evaluation.get("detailedFeedback");
                    if (detailedFeedbackObj != null) {
                        try {
                            String detailedFeedbackJson = objectMapper.writeValueAsString(detailedFeedbackObj);
                            detailedFeedback = formatDetailedFeedback(detailedFeedbackJson);
                        } catch (Exception e) {
                            logger.warn("Could not parse detailed feedback: {}", e.getMessage());
                        }
                    }
                }
                
                return new GradingEvaluation(category, score, maxPoints, feedback, scoreBand, detailedFeedback);
            } else {
                logger.warn("Could not parse JSON from OpenAI response: {}", response);
                return new GradingEvaluation(category, 0, maxPoints, "Error parsing response", "Needs Improvement", null);
            }
        } catch (Exception e) {
            logger.error("Error parsing OpenAI response: {}", response, e);
            return new GradingEvaluation(category, 0, maxPoints, "Error parsing response", "Needs Improvement", null);
        }
    }
    
    /**
     * Format detailed feedback for short answers
     */
    private String formatDetailedFeedback(String detailedFeedbackJson) {
        try {
            Map<String, Object> detailedFeedback = objectMapper.readValue(detailedFeedbackJson, Map.class);
            StringBuilder formattedFeedback = new StringBuilder();
            
            // Add overall assessment
            String overallAssessment = (String) detailedFeedback.get("overallAssessment");
            if (overallAssessment != null) {
                formattedFeedback.append(overallAssessment).append("\n\n");
            }
            
            // Add key points breakdown
            Object keyPointsObj = detailedFeedback.get("keyPoints");
            if (keyPointsObj instanceof List) {
                List<Map<String, Object>> keyPoints = (List<Map<String, Object>>) keyPointsObj;
                formattedFeedback.append("Detailed Breakdown:\n");
                
                for (int i = 0; i < keyPoints.size(); i++) {
                    Map<String, Object> point = keyPoints.get(i);
                    String pointName = (String) point.get("point");
                    Boolean isCorrect = (Boolean) point.get("correct");
                    String explanation = (String) point.get("explanation");
                    String suggestion = (String) point.get("suggestion");
                    
                    formattedFeedback.append("\nPoint ").append(i + 1).append(": ").append(pointName).append("\n");
                    
                    if (isCorrect != null && isCorrect) {
                        formattedFeedback.append("✅ CORRECT\n");
                    } else {
                        formattedFeedback.append("❌ INCORRECT\n");
                    }
                    
                    if (explanation != null) {
                        formattedFeedback.append("💡 Explanation: ").append(explanation).append("\n");
                    }
                    
                    if (suggestion != null) {
                        formattedFeedback.append("💡 Suggestion: ").append(suggestion).append("\n");
                    }
                }
            }
            
            return formattedFeedback.toString();
        } catch (Exception e) {
            logger.warn("Error formatting detailed feedback: {}", e.getMessage());
            return "Detailed feedback unavailable";
        }
    }

    /**
     * Generate overall feedback based on all category scores
     */
    public CompletableFuture<String> generateOverallFeedback(
            String submissionContent, 
            Map<String, GradingEvaluation> categoryEvaluations) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder prompt = new StringBuilder();
                prompt.append("You are an expert academic evaluator. Based on the following grading results, provide comprehensive overall feedback.\n\n");
                
                prompt.append("STUDENT SUBMISSION:\n");
                prompt.append(submissionContent).append("\n\n");
                
                prompt.append("GRADING RESULTS:\n");
                for (GradingEvaluation eval : categoryEvaluations.values()) {
                    prompt.append("- ").append(eval.category()).append(": ")
                          .append(eval.score()).append("/").append(eval.maxPoints())
                          .append(" points (").append(eval.scoreBand()).append(")\n");
                    prompt.append("  Feedback: ").append(eval.feedback()).append("\n\n");
                }
                
                prompt.append("FEEDBACK REQUIREMENTS - Be VERY detailed and engaging:\n\n");
                prompt.append("1. **OPENING ASSESSMENT** (2-3 sentences):\n");
                prompt.append("   - Start with an encouraging opening that acknowledges the student's effort\n");
                prompt.append("   - Give an honest but supportive overall impression\n\n");
                
                prompt.append("2. **DETAILED STRENGTHS** (3-4 bullet points):\n");
                prompt.append("   - Identify specific things the student did well\n");
                prompt.append("   - Explain WHY these are strengths\n");
                prompt.append("   - Use specific examples from their submission\n");
                prompt.append("   - Be encouraging and detailed\n\n");
                
                prompt.append("3. **AREAS FOR IMPROVEMENT** (3-4 bullet points):\n");
                prompt.append("   - Point out specific issues but in a constructive way\n");
                prompt.append("   - Explain the impact of these issues\n");
                prompt.append("   - Give concrete examples from their work\n");
                prompt.append("   - Frame as learning opportunities\n\n");
                
                prompt.append("4. **SPECIFIC RECOMMENDATIONS** (3-4 actionable items):\n");
                prompt.append("   - Give precise, actionable advice\n");
                prompt.append("   - Suggest specific study strategies or resources\n");
                prompt.append("   - Explain how to practice and improve\n");
                prompt.append("   - Be specific and practical\n\n");
                
                prompt.append("5. **ENCOURAGING CONCLUSION** (2-3 sentences):\n");
                prompt.append("   - End on a positive, motivating note\n");
                prompt.append("   - Highlight their potential for improvement\n");
                prompt.append("   - Express confidence in their abilities\n\n");
                
                prompt.append("**TONE**: Professional but warm, detailed but accessible, honest but encouraging.\n");
                prompt.append("**LENGTH**: Aim for 300-400 words of substantive, detailed feedback.\n");
                prompt.append("**AVOID**: Generic comments, vague praise, harsh criticism.\n\n");
                
                prompt.append("Generate comprehensive, detailed, and engaging feedback:");
                
                String response = makeOpenAIRequest(prompt.toString());
                return response.trim();
            } catch (Exception e) {
                logger.error("Error generating overall feedback", e);
                return "Unable to generate overall feedback due to technical issues.";
            }
        });
    }

    /**
     * Generate overall feedback for a submission (synchronous version)
     */
    public String generateOverallFeedbackSync(
            String submissionContent, 
            Map<String, GradingEvaluation> categoryEvaluations) {
        
        try {
            // Check if we're using a mock key
            if (apiKey.equals("mock-key-for-testing") || apiKey.equals("mock-key")) {
                return "No OpenAI API key available. Please set OPENAI_API_KEY environment variable for real AI-powered feedback.";
            }
            
            String prompt = buildOverallFeedbackPrompt(submissionContent, categoryEvaluations);
            String response = makeOpenAIRequest(prompt);
            return parseFeedbackResponse(response);
        } catch (Exception e) {
            logger.error("Error generating overall feedback", e);
            return "Error generating feedback: " + e.getMessage();
        }
    }

    /**
     * Build the overall feedback prompt for OpenAI
     */
    private String buildOverallFeedbackPrompt(
            String submissionContent, 
            Map<String, GradingEvaluation> categoryEvaluations) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert academic evaluator. Provide concise, student-friendly overall feedback.\n");
        prompt.append("Keep it short, specific, and actionable.\n\n");

        prompt.append("STUDENT SUBMISSION (for reference):\n");
        prompt.append(submissionContent).append("\n\n");

        prompt.append("GRADING SUMMARY:\n");
        for (GradingEvaluation eval : categoryEvaluations.values()) {
            prompt.append("- ").append(eval.category()).append(": ")
                  .append(eval.score()).append("/").append(eval.maxPoints())
                  .append(" (" ).append(eval.scoreBand()).append(")\n");
        }

        prompt.append("Now produce VERY CONCISE, PERSONALIZED feedback for the student:\n");
        prompt.append("- 3–5 bullets (≤ 12 words) about strengths and gaps, each with 'because ...' tied to their content.\n");
        prompt.append("- Include exactly 1 short quote (≤ 8 words) OR a paraphrase from the student's submission.\n");
        prompt.append("- End with one tailored next step that will help you.\n");
        prompt.append("- Total length ≤ 600 characters.\n");
        prompt.append("- No headings, no preamble, no generic phrases; be specific to this submission.\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse the feedback response from OpenAI
     */
    private String parseFeedbackResponse(String response) {
        // For now, just return the response as-is
        // In a real implementation, you might want to parse JSON or extract specific sections
        return response.trim();
    }
    
    /**
     * Calculate actual MCQ score by comparing student answers with correct answers
     */
    public MCQScore calculateMCQScore(String studentSubmission, String correctAnswers) {
        try {
            // Parse student answers and correct answers
            Map<String, String> studentAnswers = parseMCQAnswers(studentSubmission);
            Map<String, String> correctAnswerMap = parseMCQAnswers(correctAnswers);
            
            int totalQuestions = correctAnswerMap.size();
            int correctCount = 0;
            Map<String, String> questionResults = new HashMap<>();
            
            for (Map.Entry<String, String> entry : correctAnswerMap.entrySet()) {
                String question = entry.getKey();
                String correctAnswer = entry.getValue();
                String studentAnswer = studentAnswers.get(question);
                
                // Check if student provided an answer
                if (studentAnswer == null || studentAnswer.trim().isEmpty()) {
                    questionResults.put(question, "INCORRECT (Expected: " + correctAnswer + ", Got: NO ANSWER PROVIDED)");
                } else if (studentAnswer.equalsIgnoreCase(correctAnswer)) {
                    correctCount++;
                    questionResults.put(question, "CORRECT");
                } else {
                    questionResults.put(question, "INCORRECT (Expected: " + correctAnswer + ", Got: " + studentAnswer + ")");
                }
            }
            
            return new MCQScore(correctCount, totalQuestions, questionResults, correctAnswerMap, studentAnswers);
        } catch (Exception e) {
            logger.error("Error calculating MCQ score", e);
            return new MCQScore(0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }
    
    /**
     * Parse MCQ answers from submission text
     */
    private Map<String, String> parseMCQAnswers(String submission) {
        Map<String, String> answers = new HashMap<>();
        
        // Split by lines and look for "Answer: X" patterns
        String[] lines = submission.split("\n");
        StringBuilder currentQuestion = new StringBuilder();
        boolean inQuestion = false;
        String pendingQuestionKey = null;
        
        for (String line : lines) {
            line = line.trim();
            
            // Check if this is a question line (starts with "Question X:")
            if (line.matches("Question \\d+:.*")) {
                // Save previous question if we have one with pending answer
                if (pendingQuestionKey != null && currentQuestion.length() > 0) {
                    answers.put(pendingQuestionKey, ""); // No answer found yet
                }
                currentQuestion = new StringBuilder(line);
                pendingQuestionKey = line;
                inQuestion = true;
            }
            // Check if this is an answer line (case insensitive) - must have content after ":"
            else if (line.matches("(?i)Answer: [A-D]")) {
                if (pendingQuestionKey != null) {
                    String answer = line.substring(line.indexOf(":") + 1).trim().toUpperCase();
                    answers.put(pendingQuestionKey, answer);
                    pendingQuestionKey = null;
                    inQuestion = false;
                }
            }
            // Check for empty answer lines (Answer: with nothing after)
            else if (line.matches("(?i)Answer:\\s*$")) {
                if (pendingQuestionKey != null) {
                    answers.put(pendingQuestionKey, ""); // Explicitly mark as empty
                    pendingQuestionKey = null;
                    inQuestion = false;
                }
            }
            // If we're building a question, add this line to the current question
            else if (inQuestion && !line.isEmpty()) {
                currentQuestion.append("\n").append(line);
                // Update the question key with the full question text
                if (pendingQuestionKey != null) {
                    pendingQuestionKey = currentQuestion.toString().trim();
                }
            }
        }
        
        // Handle last question if no answer was provided
        if (pendingQuestionKey != null) {
            answers.put(pendingQuestionKey, "");
        }
        
        return answers;
    }
    
    /**
     * Record to hold MCQ scoring results
     */
    public record MCQScore(
        int correctAnswers,
        int totalQuestions,
        Map<String, String> questionResults,
        Map<String, String> correctAnswerMap,
        Map<String, String> studentAnswerMap
    ) {
        public double getPercentage() {
            return totalQuestions > 0 ? (double) correctAnswers / totalQuestions * 100.0 : 0.0;
        }
        
        public String getScoreSummary() {
            return correctAnswers + "/" + totalQuestions + " (" + String.format("%.1f", getPercentage()) + "%)";
        }
        
        public String getDetailedFeedback() {
            StringBuilder feedback = new StringBuilder();
            feedback.append("Here's the breakdown with explanations:\n\n");
            
            // Show all questions with explanations
            int questionNum = 1;
            for (Map.Entry<String, String> entry : questionResults.entrySet()) {
                String question = entry.getKey();
                String correctAnswer = correctAnswerMap.get(question);
                String studentAnswer = studentAnswerMap.get(question);
                boolean isCorrect = entry.getValue().equals("CORRECT");
                
                feedback.append("Question ").append(questionNum).append(":\n");
                feedback.append(question).append("\n");
                
                if (isCorrect) {
                    feedback.append("✅ CORRECT - Your answer: ").append(studentAnswer).append("\n");
                    feedback.append("💡 Great work! ").append(getExplanationForQuestion(question, correctAnswer, true)).append("\n\n");
                } else {
                    feedback.append("❌ INCORRECT - This question was marked incorrectly.\n");
                    feedback.append("   Your answer: ").append(studentAnswer)
                           .append(" | Correct answer: ").append(correctAnswer).append("\n");
                    feedback.append("💡 How to solve this correctly: ").append(getExplanationForQuestion(question, correctAnswer, false)).append("\n\n");
                }
                questionNum++;
            }
            
            return feedback.toString();
        }
        
        private String getExplanationForQuestion(String question, String correctAnswer, boolean wasCorrect) {
            // Generate detailed, educational explanations based on the question content
            if (question.toLowerCase().contains("synonym") || question.toLowerCase().contains("happy")) {
                if (wasCorrect) {
                    return "Excellent! You provided a correct synonym for 'happy'. Synonyms are words with similar meanings, and there are many good options like joyful, glad, cheerful, pleased, or delighted.";
                } else {
                    return "No answer provided for this question. A synonym is a word with a similar meaning. For 'happy', you could use: joyful, glad, cheerful, pleased, content, delighted, or elated. Always provide an answer even if you're unsure!";
                }
            } else if (question.toLowerCase().contains("opposite") || question.toLowerCase().contains("hot")) {
                if (wasCorrect) {
                    return "Perfect! 'Cold' is indeed the opposite of 'hot'. You correctly identified this antonym - words that have opposite meanings.";
                } else {
                    return "The opposite of 'hot' is 'cold'. Opposites (antonyms) are words with contrasting meanings. Hot refers to high temperature, while cold refers to low temperature.";
                }
            } else if (question.toLowerCase().contains("complete") || question.toLowerCase().contains("morning") || question.toLowerCase().contains("eat")) {
                if (wasCorrect) {
                    return "Great job completing the sentence! Your answer makes sense for a breakfast context and shows good understanding of meal timing.";
                } else {
                    return "No answer provided for this sentence completion. Common breakfast foods include: eggs, cereal, toast, fruit, pancakes, oatmeal, or yogurt. Think about what people typically eat in the morning!";
                }
            } else if (question.toLowerCase().contains("5 + 3") || question.toLowerCase().contains("addition")) {
                if (wasCorrect) {
                    return "Excellent! You correctly solved 5 + 3 = 8. Addition combines two quantities - when you start with 5 and add 3 more, you get 8 total. This demonstrates solid understanding of basic arithmetic operations.";
                } else {
                    return "Let's work through this step-by-step: 5 + 3 means we start with 5 and add 3 more. You can visualize this as 5 objects plus 3 more objects = 8 total objects. Or count upward from 5: 6, 7, 8. The correct answer is " + correctAnswer + " which represents 8.";
                }
            } else if (question.toLowerCase().contains("even") || question.toLowerCase().contains("number")) {
                if (wasCorrect) {
                    return "Perfect! You correctly identified the even number. Even numbers are integers that can be divided by 2 with no remainder (like 2, 4, 6, 8, 10...). The number 4 is even because 4 ÷ 2 = 2 exactly, with no remainder.";
                } else {
                    return "Let's clarify even vs. odd numbers: Even numbers are divisible by 2 with no remainder. You can test this by seeing if you can group the number into pairs with nothing left over. For example: 4 objects can make 2 pairs (even), but 5 objects would have 1 left over (odd). The correct answer is " + correctAnswer + " representing the even number.";
                }
            } else if (question.toLowerCase().contains("10 - 6") || question.toLowerCase().contains("subtraction")) {
                if (wasCorrect) {
                    return "Great work! You correctly calculated 10 - 6 = 4. Subtraction means 'taking away' - when you start with 10 items and remove 6, you're left with 4 items. This shows good grasp of subtraction concepts.";
                } else {
                    return "Let's solve 10 - 6 together: Start with 10 and subtract (take away) 6. You can count backwards from 10: 9, 8, 7, 6, 5, 4. Or think of it as having 10 fingers, putting down 6 fingers, and counting how many are still up (4). The correct answer is " + correctAnswer + " representing 4.";
                }
            } else if (question.toLowerCase().contains("tell")) {
                if (wasCorrect) {
                    return "Excellent understanding! The 'tell' pattern is indeed a fire-and-forget messaging approach. You send a message to an actor without expecting or waiting for a response. This is the most common and efficient pattern for one-way communication in actor systems.";
                } else {
                    return "The 'tell' pattern (!) is fire-and-forget messaging - you send a message to an actor without waiting for a response. It's asynchronous and non-blocking, making it ideal for commands and notifications where you don't need a reply. The correct answer is " + correctAnswer + ".";
                }
            } else if (question.toLowerCase().contains("ask")) {
                if (wasCorrect) {
                    return "Perfect! The 'ask' pattern does return a Future that will eventually contain the response. This pattern enables request-response communication with timeouts and error handling, essential for queries where you need data back from the actor.";
                } else {
                    return "The 'ask' pattern (?) returns a CompletableFuture/Future object that will eventually contain the response from the target actor. Unlike 'tell', this is request-response communication - you send a message and wait for a reply. This is crucial for queries and operations where you need data back. The correct answer is " + correctAnswer + ".";
                }
            } else if (question.toLowerCase().contains("forward")) {
                if (wasCorrect) {
                    return "Excellent! Forward does preserve the original sender when passing messages to another actor. This maintains the sender's identity throughout the message chain, which is crucial for proper response routing in distributed systems.";
                } else {
                    return "The 'forward' pattern preserves the original sender's identity when passing a message to another actor. Instead of the forwarding actor becoming the new sender, the original sender is maintained. This is essential for delegation patterns where responses should go back to the original requester. The correct answer is " + correctAnswer + ".";
                }
            } else if (question.toLowerCase().contains("cluster")) {
                if (wasCorrect) {
                    return "Great understanding! Akka Cluster provides location transparency and fault tolerance, allowing actors to communicate across multiple JVMs and machines as if they were local, with automatic failure detection and recovery.";
                } else {
                    return "Akka Cluster enables distributed actor systems by providing location transparency (actors can communicate across machines as if local) and fault tolerance (automatic detection and handling of node failures). It manages cluster membership, routing, and failure detection automatically. The correct answer is " + correctAnswer + ".";
                }
            } else if (question.toLowerCase().contains("supervisor")) {
                if (wasCorrect) {
                    return "Perfect! Supervisor Strategy defines how parent actors handle child actor failures, with options like restart (recreate), stop (terminate), escalate (pass up), or resume (continue). This is fundamental to actor system resilience.";
                } else {
                    return "Supervisor Strategy defines how parent actors respond to child actor failures. The four main strategies are: Restart (recreate the child), Stop (terminate the child), Escalate (pass the failure up to the parent's supervisor), and Resume (continue as if nothing happened). This creates hierarchical fault tolerance. The correct answer is " + correctAnswer + ".";
                }
            } else if (question.toLowerCase().contains("dispatcher")) {
                if (wasCorrect) {
                    return "Excellent! Dispatchers manage thread pools and control how actor messages are executed. They determine which threads process messages, affecting performance and concurrency characteristics of your actor system.";
                } else {
                    return "Dispatchers control the execution environment for actors by managing thread pools and determining how messages are processed. Different dispatcher types (like default, pinned, or fork-join) offer different performance characteristics. They're crucial for optimizing actor system performance. The correct answer is " + correctAnswer + ".";
                }
            } else {
                if (wasCorrect) {
                    return "Correct! You demonstrated good understanding of this concept. Keep applying this analytical thinking to similar problems.";
                } else {
                    return "This concept requires careful consideration. Review the relevant materials, think through the logic step-by-step, and consider how the different options relate to the core principles. The correct answer is " + correctAnswer + ".";
                }
            }
        }
    }

    /**
     * Represents a grading evaluation result
     */
    public record GradingEvaluation(
            String category,
            int score,
            int maxPoints,
            String feedback,
            String scoreBand,
            String detailedFeedback
    ) {
        public GradingEvaluation(String category, int score, int maxPoints, String feedback, String scoreBand) {
            this(category, score, maxPoints, feedback, scoreBand, null);
        }
        
        public double getPercentage() {
            return maxPoints > 0 ? (double) score / maxPoints * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %d/%d points (%.1f%%) - %s", 
                    category, score, maxPoints, getPercentage(), scoreBand);
        }
    }
} 