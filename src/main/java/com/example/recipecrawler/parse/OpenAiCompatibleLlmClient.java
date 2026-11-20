package com.example.recipecrawler.parse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LlmClient} over the OpenAI {@code POST /chat/completions} shape, which
 * every major hosted provider and local runner (Ollama, llama.cpp, vLLM) speaks.
 * A plain {@link RestClient} call — same as every other outbound call here.
 * The {@code Authorization: Bearer} header is sent only when {@code llm.api-key}
 * is set (a local Ollama ignores it; supply any non-blank value there).
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient http;
    private final String completionsUrl;
    private final String model;
    private final String apiKey;
    private final boolean jsonMode;
    private final double temperature;
    private final int maxOutputTokens;

    public OpenAiCompatibleLlmClient(@Qualifier("llmClient") RestClient llmClient,
                                     @Value("${llm.base-url:}") String baseUrl,
                                     @Value("${llm.model:}") String model,
                                     @Value("${llm.api-key:}") String apiKey,
                                     @Value("${llm.json-mode:true}") boolean jsonMode,
                                     @Value("${llm.temperature:0}") double temperature,
                                     @Value("${llm.max-output-tokens:2048}") int maxOutputTokens) {
        this.http = llmClient;
        String base = (baseUrl == null ? "" : baseUrl.trim()).replaceAll("/+$", "");
        this.completionsUrl = base.isEmpty() ? "" : base + "/chat/completions";
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.jsonMode = jsonMode;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public boolean isConfigured() {
        return !completionsUrl.isEmpty() && !model.isEmpty() && !apiKey.isEmpty();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxOutputTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        RestClient.RequestBodySpec spec = http.post().uri(completionsUrl).contentType(MediaType.APPLICATION_JSON);
        if (!apiKey.isEmpty()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        JsonNode resp = spec.body(body).retrieve().body(JsonNode.class);

        if (resp == null) {
            throw new IllegalStateException("the LLM returned an empty response");
        }
        JsonNode content = resp.path("choices").path(0).path("message").path("content");
        String text = content.isString() ? content.asString("") : (content.isMissingNode() ? null : content.toString());
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("the LLM response carried no message content");
        }
        return text;
    }
}
