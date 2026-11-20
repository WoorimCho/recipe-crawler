package com.example.recipecrawler.parse;

/**
 * A single chat completion against an OpenAI-compatible {@code /chat/completions}
 * endpoint. Provider-neutral on purpose: point {@code llm.base-url} at OpenAI,
 * Anthropic ({@code https://api.anthropic.com/v1}), Gemini's OpenAI endpoint,
 * Groq, OpenRouter, a local Ollama ({@code http://localhost:11434/v1}), etc. The
 * one implementation ({@link OpenAiCompatibleLlmClient}) speaks that shared wire
 * format — no vendor SDK.
 */
public interface LlmClient {

    /**
     * True only when {@code llm.base-url}, {@code llm.model} and {@code llm.api-key}
     * are all set. When false the free-text fallback is skipped and the caller
     * gets the usual 422.
     */
    boolean isConfigured();

    /**
     * One completion; returns the assistant message text (a JSON object, here).
     *
     * @throws org.springframework.web.client.ResourceAccessException the endpoint
     *         is unreachable or its circuit breaker is open
     * @throws org.springframework.web.client.RestClientResponseException the
     *         endpoint answered with an error status (bad key, bad request, 429)
     */
    String complete(String systemPrompt, String userPrompt);
}
