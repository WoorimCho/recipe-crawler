package com.example.recipecrawler.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/**
 * Wraps every outbound HTTP call to one target (a catalogue service, or the
 * recipe source site) in a named {@link CircuitBreaker}: a connect / read
 * timeout or a 5xx is a fault, a 4xx passes through, and while the breaker is
 * OPEN the call is rejected immediately as a {@link ResourceAccessException}.
 * Per-app copy of the BFF's interceptor.
 */
public final class CircuitBreakerInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker breaker;

    public CircuitBreakerInterceptor(CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        try {
            return breaker.executeCallable(() -> {
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().is5xxServerError()) {
                    throw new IOException(breaker.getName() + " returned HTTP "
                            + response.getStatusCode().value());
                }
                return response;
            });
        } catch (CallNotPermittedException open) {
            throw new ResourceAccessException(
                    breaker.getName() + " is unavailable (circuit " + breaker.getState() + ")");
        } catch (IOException io) {
            throw new ResourceAccessException(breaker.getName() + " call failed: " + io.getMessage(), io);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new ResourceAccessException(breaker.getName() + " call failed", new IOException(e));
        }
    }
}
