package com.barinventory.invoice.exception;

public class ExtractionFailedException extends RuntimeException {
    public ExtractionFailedException() { super(); }
    public ExtractionFailedException(String message) { super(message); }
    public ExtractionFailedException(String message, Throwable cause) { super(message, cause); }
}