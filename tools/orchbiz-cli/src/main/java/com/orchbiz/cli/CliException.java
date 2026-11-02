package com.orchbiz.cli;

/** A user-facing failure; the message is printed without a stack trace. */
public class CliException extends RuntimeException {
    public CliException(String message) {
        super(message);
    }
}
