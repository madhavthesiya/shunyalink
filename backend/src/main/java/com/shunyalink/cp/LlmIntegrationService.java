package com.shunyalink.cp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

@Service
public class LlmIntegrationService {

    private final RestTemplate restTemplate;
    private final String geminiApiKey;
    private final String groqApiKey;
    
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public LlmIntegrationService(RestTemplate restTemplate, 
                                 @Value("${app.gemini.api-key}") String geminiApiKey,
                                 @Value("${app.groq.api-key:}") String groqApiKey) {
        this.restTemplate = restTemplate;
        this.geminiApiKey = geminiApiKey;
        this.groqApiKey = groqApiKey;
    }

    /**
     * Roast the user's CP stats using Hybrid AI (Groq prioritized for speed).
     */
    public String roastProfile(String statsContext) {
        String prompt = "Act like a sarcastic MAANG interviewer. I am going to give you a user's competitive programming stats. " +
                "Your job is to roast them in exactly 2 witty, sarcastic sentences. " +
                "Be brutal but keep it professional (no profanity). " +
                "Stats: " + statsContext;

        boolean groqOk = isKeyValid(groqApiKey);
        boolean geminiOk = isKeyValid(geminiApiKey);

        System.out.println("AI Roast Attempt - Groq Key Valid: " + groqOk + ", Gemini Key Valid: " + geminiOk);

        // Try Groq First (Faster & Higher Limits)
        if (groqOk) {
            try {
                return callGroq(prompt);
            } catch (Exception e) {
                System.err.println("Groq roast failed: " + e.getMessage());
            }
        }

        // Fallback to Gemini
        if (geminiOk) {
            try {
                return callGemini(prompt);
            } catch (Exception e) {
                System.err.println("Gemini roast failed: " + e.getMessage());
            }
        }

        if (!groqOk && !geminiOk) {
            return "The AI roast engine is currently offline. (API Keys not configured in backend)";
        }
        
        return "The AI roast engine is currently overloaded. Please try again in a few minutes.";
    }

    /**
     * Unified processing: Cleans a raw title and provides a category in a single step.
     * Returns "TITLE|CATEGORY" string.
     */
    public String processMetadata(String rawTitle, String rawDescription) {
        String prompt = "You are a creative content strategist and web expert.\n" +
                "I will provide a raw website title and its description. Your goal is to transform them into a high-converting, professional heading.\n\n" +
                "RULES FOR THE CLEAN_TITLE:\n" +
                "- Create a VIBRANT, engaging heading (max 60 chars).\n" +
                "- Capture the 'hook' of the page (e.g., instead of 'Google', use 'Google - Search the world').\n" +
                "- Remove technical junk like '.html', 'index', or 'Welcome to...'.\n" +
                "- If the raw title is weak, use the description to craft something much more relatable and descriptive.\n\n" +
                "RULES FOR THE CATEGORY:\n" +
                "- Select EXACTLY one: [VIDEO, GITHUB, DOCUMENTATION, SOCIAL_MEDIA, PORTFOLIO, BLOG, GENERAL].\n" +
                "- This is used for backend indexing only.\n\n" +
                "OUTPUT FORMAT: CLEAN_TITLE|CATEGORY\n" +
                "Return ONLY that one line. No quotes, no intro text.\n\n" +
                "Raw Title: \"" + rawTitle + "\"\n" +
                "Description: \"" + rawDescription + "\"";

        // Try Groq First (Faster)
        if (isKeyValid(groqApiKey)) {
            try {
                return callGroq(prompt);
            } catch (Exception e) {
                // Fallback
            }
        }

        // Try Gemini
        if (isKeyValid(geminiApiKey)) {
            try {
                return callGemini(prompt);
            } catch (Exception e) {
                // Fallback
            }
        }

        // Default fallback if AI fails
        return rawTitle + "|GENERAL";
    }

    /**
     * Categorize a URL based on its metadata using Hybrid AI (Gemini prioritized for reasoning).
     */
    public String categorizeUrl(String title, String description) {
        String prompt = "Review this website title and description and categorize it into exactly one word from this list: " +
                "[VIDEO, GITHUB, DOCUMENTATION, SOCIAL_MEDIA, PORTFOLIO, BLOG, GENERAL]. " +
                "Only return the category word. " +
                "Title: " + title + " | Description: " + description;

        // Try Gemini First (Better reasoning for classification)
        if (isKeyValid(geminiApiKey)) {
            try {
                return callGemini(prompt).toUpperCase().trim();
            } catch (Exception e) {
                // Fallback to Groq
            }
        }

        // Fallback to Groq
        if (isKeyValid(groqApiKey)) {
            try {
                return callGroq(prompt).toUpperCase().trim();
            } catch (Exception e) {
                // Return default
            }
        }

        return "GENERAL";
    }

    private boolean isKeyValid(String key) {
        return key != null && !key.trim().isEmpty() && !key.contains("${");
    }

    @SuppressWarnings("unchecked")
    private String callGroq(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        body.put("messages", List.of(message));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(GROQ_API_URL, request, Map.class);
        
        if (response == null || !response.containsKey("choices")) {
            throw new RuntimeException("Groq failed");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> messageContent = (Map<String, Object>) choices.get(0).get("message");
        return (String) messageContent.get("content");
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        
        Map<String, Object> contents = new HashMap<>();
        contents.put("parts", List.of(parts));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(contents));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(GEMINI_API_URL + geminiApiKey, request, Map.class);
            if (response == null || !response.containsKey("candidates")) {
                throw new RuntimeException("Gemini returned empty response");
            }
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> resParts = (List<Map<String, Object>>) content.get("parts");
            return (String) resParts.get(0).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Gemini failed", e);
        }
    }
}
