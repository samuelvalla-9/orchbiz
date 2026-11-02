package com.orchbiz.leadapi.lead;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** Client-facing error with a stable machine-readable code. Never carries internal details. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    public ApiException(HttpStatus status, String code, Map<String, String> fieldErrors) {
        super(code);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException validation(Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", fieldErrors);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
