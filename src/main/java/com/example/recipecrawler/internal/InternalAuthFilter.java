package com.example.recipecrawler.internal;

import com.example.recipecrawler.internal.InternalAuth.InvalidInternalAuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Every {@code /api/**} call (preview / commit / crawl) must carry a valid
 * {@link InternalAuth#HEADER} — it triggers outbound page fetches and catalogue
 * writes, so it's an internal-only surface. Disable with
 * {@code internal-auth.enabled=false} (tests do).
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final String secret;

    public InternalAuthFilter(@Value("${internal-auth.enabled:true}") boolean enabled,
                              @Value("${internal-auth.secret:}") String secret) {
        this.enabled = enabled;
        this.secret = enabled ? InternalAuth.requireSecret(secret) : secret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            InternalAuth.verify(secret, request.getHeader(InternalAuth.HEADER),
                    request.getMethod(), request.getRequestURI());
        } catch (InvalidInternalAuthException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"internal auth: " + e.getMessage() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
