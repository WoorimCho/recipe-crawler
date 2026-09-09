package com.example.recipecrawler.web;

import com.example.recipecrawler.parse.SchemaOrgRecipeParser.NoRecipeFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Maps the importer's failure modes to RFC 9457 problem responses. */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** The page loaded but carried no schema.org Recipe we could read. */
    @ExceptionHandler(NoRecipeFoundException.class)
    ProblemDetail noRecipe(NoRecipeFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** Bad URL, non-http scheme, empty body, etc. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ProblemDetail badInput(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("request body is invalid");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** The source site or a catalogue returned an error status. */
    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail downstreamStatus(RestClientResponseException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "a downstream call failed: HTTP " + ex.getStatusCode().value());
        p.setTitle("Bad Gateway");
        return p;
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail downstreamUnavailable(RestClientException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "could not reach the source page or a catalogue service: " + ex.getMessage());
        p.setTitle("Bad Gateway");
        return p;
    }
}
