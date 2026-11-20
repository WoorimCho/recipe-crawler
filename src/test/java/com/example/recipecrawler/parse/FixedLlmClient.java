package com.example.recipecrawler.parse;

import java.util.function.Supplier;

/**
 * Test double for {@link LlmClient}: either "not configured", or configured and
 * returning a canned completion (or throwing a canned exception). Public so both
 * {@code parse} and {@code web} test packages can share it.
 */
public final class FixedLlmClient implements LlmClient {

    private final boolean configured;
    private final Supplier<String> reply;

    private FixedLlmClient(boolean configured, Supplier<String> reply) {
        this.configured = configured;
        this.reply = reply;
    }

    public static FixedLlmClient disabled() {
        return new FixedLlmClient(false, () -> {
            throw new AssertionError("complete() called on a disabled LlmClient");
        });
    }

    public static FixedLlmClient returning(String completion) {
        return new FixedLlmClient(true, () -> completion);
    }

    public static FixedLlmClient throwing(RuntimeException ex) {
        return new FixedLlmClient(true, () -> {
            throw ex;
        });
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return reply.get();
    }
}
