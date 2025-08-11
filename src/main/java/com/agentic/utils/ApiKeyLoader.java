package com.agentic.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to load API keys from environment variables or configuration files
 */
public class ApiKeyLoader {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyLoader.class);
    
    /**
     * Load OpenAI API key from environment variable or config file
     * Priority: 1. Environment variable, 2. Config file, 3. Default mock key
     */
    public static String loadOpenAIKey() {
        // First try environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // Try .env file in working directory
            apiKey = loadFromDotEnv("OPENAI_API_KEY");
        }
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            logger.info("Using OpenAI API key from environment variable");
            return apiKey.trim();
        }
        
        // Then try config file
        try {
            Properties config = loadConfigFile();
            apiKey = config.getProperty("openai.api.key");
            if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("your-openai-api-key-here")) {
                logger.info("Using OpenAI API key from config file");
                return apiKey.trim();
            }
        } catch (Exception e) {
            logger.warn("Could not load config file: {}", e.getMessage());
        }
        
        // Fallback to mock key
        logger.warn("No valid OpenAI API key found, using mock client");
        return "mock-key";
    }

    // OpenAI-only configuration: base URL and model are fixed unless overridden elsewhere
    
    /**
     * Load configuration from config.properties file
     */
    private static Properties loadConfigFile() throws IOException {
        Properties config = new Properties();
        // Try to load from classpath first
        try (InputStream input = ApiKeyLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
                return config;
            }
        }
        // Try to load from current working directory
        try (InputStream input = new java.io.FileInputStream("config.properties")) {
            config.load(input);
            return config;
        } catch (IOException ignored) {}

        // If no config file found, return empty properties
        logger.warn("No config.properties file found");
        return config;
    }

    /**
     * Load a key from a .env file in the working directory (simple parser).
     */
    private static String loadFromDotEnv(String keyName) {
        java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
        if (!java.nio.file.Files.exists(envPath)) {
            return null;
        }
        try {
            for (String rawLine : java.nio.file.Files.readAllLines(envPath)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                if (k.equals(keyName)) {
                    logger.info("Using OpenAI API key from .env file");
                    return v;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to read .env file: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if we have a valid API key (not mock)
     */
    public static boolean hasValidApiKey() {
        String apiKey = loadOpenAIKey();
        return apiKey != null && !apiKey.equals("mock-key") && !apiKey.equals("your-openai-api-key-here");
    }
} 