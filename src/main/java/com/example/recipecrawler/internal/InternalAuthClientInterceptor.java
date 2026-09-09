package com.example.recipecrawler.internal;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Signs outbound catalogue calls with {@link InternalAuth#HEADER}. The crawler
 * never acts for a specific user, so the acting-user field is always none.
 */
public final class InternalAuthClientInterceptor implements ClientHttpRequestInterceptor {

    private final String secret;

    public InternalAuthClientInterceptor(String secret) {
        this.secret = secret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(InternalAuth.HEADER,
                InternalAuth.header(secret, request.getMethod().name(), request.getURI().getRawPath(), null));
        return execution.execute(request, body);
    }
}
