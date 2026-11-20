package com.example.recipecrawler.parse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmClientTest {

    private static final String OK_BODY = """
            {"choices":[{"message":{"role":"assistant","content":"{\\"name\\":\\"Cake\\"}"}}]}""";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    private OpenAiCompatibleLlmClient client(String baseUrl, String model, String key, boolean jsonMode) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiCompatibleLlmClient(builder.build(), baseUrl, model, key, jsonMode, 0.0, 2048);
    }

    @Test
    void postsChatCompletionsAndReturnsTheMessageContent() {
        OpenAiCompatibleLlmClient c = client("https://api.test/v1", "some-model", "sk-abc", true);
        server.expect(requestTo("https://api.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-abc"))
                .andExpect(jsonPath("$.model").value("some-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(c.complete("sys", "usr")).isEqualTo("{\"name\":\"Cake\"}");
        server.verify();
    }

    @Test
    void omitsResponseFormatWhenJsonModeOff() {
        OpenAiCompatibleLlmClient c = client("https://api.test/v1", "m", "k", false);
        server.expect(requestTo("https://api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        c.complete("sys", "usr");
        server.verify();
    }

    @Test
    void trimsATrailingSlashOnTheBaseUrl() {
        OpenAiCompatibleLlmClient c = client("https://api.test/v1/", "m", "k", true);
        server.expect(requestTo("https://api.test/v1/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        c.complete("sys", "usr");
        server.verify();
    }

    @Test
    void emptyContentIsAnError() {
        OpenAiCompatibleLlmClient c = client("https://api.test/v1", "m", "k", true);
        server.expect(requestTo("https://api.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"\"}}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> c.complete("sys", "usr")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isConfiguredOnlyWhenBaseUrlModelAndKeyAreAllSet() {
        assertThat(client("https://api.test/v1", "m", "k", true).isConfigured()).isTrue();
        assertThat(client("", "m", "k", true).isConfigured()).isFalse();
        assertThat(client("https://api.test/v1", "", "k", true).isConfigured()).isFalse();
        assertThat(client("https://api.test/v1", "m", "", true).isConfigured()).isFalse();
    }
}
