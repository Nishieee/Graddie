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
            prompt.append("STRICT MCQ EVALUATION RULES:\n");
            prompt.append("1. ONLY count exact matches between student answers and correct answers\n");
            prompt.append("2. A is NOT the same as B, C, or D - be EXACT\n");
            prompt.append("3. Calculate score as: (correct_answers / total_questions) * max_points\n");
            prompt.append("4. Do NOT give partial credit for MCQ - either correct (1) or wrong (0)\n");
            prompt.append("5. Count EVERY question, do not skip any\n");
            prompt.append("6. For each question, provide a brief explanation of why the correct answer is right\n");
            prompt.append("7. For incorrect answers, explain why the student's choice was wrong\n");
            prompt.append("8. Verify your counting twice before assigning the final score\n\n");
            
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
            prompt.append("SHORT ANSWER EVALUATION RULES:\n");
            prompt.append("1. IMPORTANT: Be generous with scoring - this is about understanding, not perfection\n");
            prompt.append("2. SCORING GUIDE:\n");
            prompt.append("   - 1.0 = Perfect, complete answer\n");
            prompt.append("   - 0.8 = Very good, shows clear understanding\n");
            prompt.append("   - 0.7 = Good understanding, minor details missing\n");
            prompt.append("   - 0.6 = Shows understanding of main concept\n");
            prompt.append("   - 0.4 = Partial understanding, some correct elements\n");
            prompt.append("   - 0.2 = Minimal understanding shown\n");
            prompt.append("   - 0.0 = Completely wrong or nonsensical\n");
            prompt.append("3. If student shows ANY understanding of the concept, give AT LEAST 0.6\n");
            prompt.append("4. Only use 0.0-0.3 for answers that are totally wrong or make no sense\n");
            prompt.append("5. Focus on what the student got RIGHT, not what's missing\n");
            prompt.append("6. Provide detailed per-question feedback showing right/wrong with explanations\n\n");
            
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
            prompt.append("ESSAY EVALUATION (be brief and actionable):\n");
            prompt.append("- Focus on 1-2 key strengths and 1-2 key improvements.\n");
            prompt.append("- Keep feedback concise (≤ 350 characters).\n");
            prompt.append("Return your response in this JSON format:\n");
            prompt.append("{\n");
            prompt.append("  \"score\": <number of points awarded>,\n");
            prompt.append("  \"outOf\": ").append(maxPoints).append(",\n");
            prompt.append("  \"scoreBand\": \"<Excellent|Good|Fair|Needs Improvement>\",\n");
            prompt.append("  \"feedback\": \"<max 3 short sentences (≤ 350 chars), no headings>\"\n");
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
                
                prompt.append("Please provide:\n");
                prompt.append("1. A summary of the student's overall performance\n");
                prompt.append("2. Key strengths demonstrated\n");
                prompt.append("3. Specific areas for improvement\n");
                prompt.append("4. Constructive suggestions for future work\n");
                prompt.append("5. Overall assessment of the submission quality\n\n");
                prompt.append("Provide thoughtful, encouraging, and constructive feedback:");
                
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

        prompt.append("Now produce VERY CONCISE feedback:\n");
        prompt.append("- 3–5 bullet points, each ≤ 12 words.\n");
        prompt.append("- 1 short next-step sentence at the end.\n");
        prompt.append("- Total length ≤ 600 characters.\n");
        prompt.append("- No headings, no preamble, no quotes, no repetition.\n");
        prompt.append("- Be specific to the submission; avoid generic advice.\n");
        
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
                
                if (studentAnswer != null && studentAnswer.equalsIgnoreCase(correctAnswer)) {
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
        
        for (String line : lines) {
            line = line.trim();
            
            // Check if this is a question line (contains "A." which indicates MCQ options)
            if (line.contains("A.") && line.contains("B.") && line.contains("C.") && line.contains("D.")) {
                if (inQuestion && currentQuestion.length() > 0) {
                    // Save previous question if we have one
                    String questionText = currentQuestion.toString().trim();
                    if (!questionText.isEmpty()) {
                        answers.put(questionText, ""); // Will be filled when we find the answer
                    }
                }
                currentQuestion = new StringBuilder(line);
                inQuestion = true;
            }
            // Check if this is an answer line
            else if (line.matches("Answer: [A-D]")) {
                if (inQuestion && currentQuestion.length() > 0) {
                    String questionText = currentQuestion.toString().trim();
                    String answer = line.substring(line.indexOf(":") + 1).trim();
                    answers.put(questionText, answer);
                    currentQuestion = new StringBuilder();
                    inQuestion = false;
                }
            }
            // If we're in a question, add this line to the current question
            else if (inQuestion && !line.isEmpty()) {
                currentQuestion.append("\n").append(line);
            }
            // If we see a line that looks like a question (not empty, not an answer)
            else if (!line.isEmpty() && !line.matches("Answer: [A-D]") && !inQuestion) {
                currentQuestion = new StringBuilder(line);
                inQuestion = true;
            }
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
                    feedback.append("✅ CORRECT - Answer: ").append(correctAnswer).append("\n");
                    feedback.append("💡 Explanation: ").append(getExplanationForQuestion(question, correctAnswer, true)).append("\n\n");
                } else {
                    feedback.append("❌ INCORRECT - Your answer: ").append(studentAnswer)
                           .append(", Correct answer: ").append(correctAnswer).append("\n");
                    feedback.append("💡 Explanation: ").append(getExplanationForQuestion(question, correctAnswer, false)).append("\n\n");
                }
                questionNum++;
            }
            
            return feedback.toString();
        }
        
        private String getExplanationForQuestion(String question, String correctAnswer, boolean wasCorrect) {
            // Generate basic explanations based on common Akka concepts
            if (question.toLowerCase().contains("tell")) {
                return "'tell' is a fire-and-forget message pattern that sends a message without waiting for a response.";
            } else if (question.toLowerCase().contains("ask")) {
                return "'ask' returns a Future object that will eventually contain the response from the target actor.";
            } else if (question.toLowerCase().contains("forward")) {
                return "'forward' preserves the original sender when passing a message to another actor, maintaining the sender's identity.";
            } else if (question.toLowerCase().contains("cluster")) {
                return "Akka Cluster provides location transparency and fault tolerance for distributed actor systems.";
            } else if (question.toLowerCase().contains("supervisor")) {
                return "Supervisor Strategy handles child actor failures and defines how to respond (restart, stop, escalate, resume).";
            } else if (question.toLowerCase().contains("dispatcher")) {
                return "Dispatchers control how messages are executed, managing thread pools for actor message processing.";
            } else {
                return "Review the Akka documentation for this concept to understand the correct behavior.";
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