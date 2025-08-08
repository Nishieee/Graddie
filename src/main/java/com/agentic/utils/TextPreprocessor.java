package com.agentic.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for text preprocessing operations
 */
public class TextPreprocessor {
    private static final Logger logger = LoggerFactory.getLogger(TextPreprocessor.class);
    
    // Common English stopwords
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he", "in", "is", "it", "its",
        "of", "on", "that", "the", "to", "was", "will", "with", "the", "this", "but", "they", "have", "had",
        "what", "said", "each", "which", "she", "do", "how", "their", "if", "up", "out", "many", "then", "them",
        "these", "so", "some", "her", "would", "make", "like", "into", "him", "time", "two", "more", "go", "no",
        "way", "could", "my", "than", "first", "been", "call", "who", "its", "now", "find", "long", "down",
        "day", "did", "get", "come", "made", "may", "part", "over", "new", "sound", "take", "only", "little",
        "work", "know", "place", "year", "live", "me", "back", "give", "most", "very", "after", "thing", "our",
        "just", "name", "good", "sentence", "man", "think", "say", "great", "where", "help", "through", "much",
        "before", "line", "right", "too", "mean", "old", "any", "same", "tell", "boy", "follow", "came", "want",
        "show", "also", "around", "form", "three", "small", "set", "put", "end", "does", "another", "well",
        "large", "must", "big", "even", "such", "because", "turn", "here", "why", "ask", "went", "men", "read",
        "need", "land", "different", "home", "us", "move", "try", "kind", "hand", "picture", "again", "change",
        "off", "play", "spell", "air", "away", "animal", "house", "point", "page", "letter", "mother", "answer",
        "found", "study", "still", "learn", "should", "America", "world", "high", "every", "near", "add", "food",
        "between", "own", "below", "country", "plant", "last", "school", "father", "keep", "tree", "never",
        "start", "city", "earth", "eye", "light", "thought", "head", "under", "story", "saw", "left", "don't",
        "few", "while", "along", "might", "close", "something", "seem", "next", "hard", "open", "example",
        "begin", "life", "always", "those", "both", "paper", "together", "got", "group", "often", "run",
        "important", "until", "children", "side", "feet", "car", "mile", "night", "walk", "white", "sea",
        "began", "grow", "took", "river", "four", "carry", "state", "once", "book", "hear", "stop", "without",
        "second", "later", "miss", "idea", "enough", "eat", "face", "watch", "far", "Indian", "real", "almost",
        "let", "above", "girl", "sometimes", "mountain", "cut", "young", "talk", "soon", "list", "song",
        "being", "leave", "family", "it's", "body", "music", "color", "stand", "sun", "questions", "fish",
        "area", "mark", "dog", "horse", "birds", "problem", "complete", "room", "knew", "since", "ever",
        "piece", "told", "usually", "didn't", "friends", "easy", "heard", "order", "red", "door", "sure",
        "become", "top", "ship", "across", "today", "during", "short", "better", "best", "however", "low",
        "hours", "black", "products", "happened", "whole", "measure", "remember", "early", "waves", "reached",
        "listen", "wind", "rock", "space", "covered", "fast", "several", "hold", "himself", "toward", "five",
        "step", "morning", "passed", "vowel", "true", "hundred", "against", "pattern", "numeral", "table",
        "north", "slowly", "money", "map", "farm", "pulled", "draw", "voice", "seen", "cold", "cried", "plan",
        "notice", "south", "sing", "war", "ground", "fall", "king", "town", "I'll", "unit", "figure", "certain",
        "field", "travel", "wood", "fire", "upon"
    ));

    /**
     * Preprocess text by removing stopwords, cleaning punctuation, and normalizing
     */
    public static String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // Convert to lowercase
        String processed = text.toLowerCase();
        
        // Remove extra whitespace
        processed = processed.replaceAll("\\s+", " ");
        
        // Remove punctuation except for periods, commas, and question marks
        processed = processed.replaceAll("[^a-zA-Z0-9\\s.,?!]", "");
        
        // Remove stopwords
        processed = removeStopwords(processed);
        
        // Final cleanup
        processed = processed.trim();
        
        logger.debug("Preprocessed text: {} -> {} characters", text.length(), processed.length());
        return processed;
    }

    /**
     * Remove stopwords from text
     */
    private static String removeStopwords(String text) {
        String[] words = text.split("\\s+");
        List<String> filteredWords = new ArrayList<>();
        
        for (String word : words) {
            if (!STOPWORDS.contains(word.toLowerCase()) && word.length() > 1) {
                filteredWords.add(word);
            }
        }
        
        return String.join(" ", filteredWords);
    }

    /**
     * Extract key terms from text (words that appear frequently)
     */
    public static List<String> extractKeyTerms(String text, int minFrequency) {
        String processed = preprocessText(text);
        String[] words = processed.split("\\s+");
        
        Map<String, Integer> wordFrequency = new HashMap<>();
        for (String word : words) {
            if (word.length() > 2) { // Only consider words longer than 2 characters
                wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
            }
        }
        
        return wordFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= minFrequency)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20) // Top 20 terms
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Calculate text similarity using cosine similarity
     */
    public static double calculateSimilarity(String text1, String text2) {
        String processed1 = preprocessText(text1);
        String processed2 = preprocessText(text2);
        
        Map<String, Integer> vector1 = createWordVector(processed1);
        Map<String, Integer> vector2 = createWordVector(processed2);
        
        return cosineSimilarity(vector1, vector2);
    }

    /**
     * Create word frequency vector
     */
    private static Map<String, Integer> createWordVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        String[] words = text.split("\\s+");
        
        for (String word : words) {
            if (word.length() > 2) {
                vector.put(word, vector.getOrDefault(word, 0) + 1);
            }
        }
        
        return vector;
    }

    /**
     * Calculate cosine similarity between two word vectors
     */
    private static double cosineSimilarity(Map<String, Integer> vector1, Map<String, Integer> vector2) {
        Set<String> allWords = new HashSet<>(vector1.keySet());
        allWords.addAll(vector2.keySet());
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String word : allWords) {
            int freq1 = vector1.getOrDefault(word, 0);
            int freq2 = vector2.getOrDefault(word, 0);
            
            dotProduct += freq1 * freq2;
            norm1 += freq1 * freq1;
            norm2 += freq2 * freq2;
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Get word count statistics
     */
    public static Map<String, Integer> getWordCountStats(String text) {
        String processed = preprocessText(text);
        String[] words = processed.split("\\s+");
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total_words", words.length);
        stats.put("unique_words", (int) Arrays.stream(words).distinct().count());
        stats.put("characters", text.length());
        stats.put("sentences", text.split("[.!?]+").length);
        
        return stats;
    }
} 