package com.barinventory.invoice.exception;
public class PdfProcessingException extends RuntimeException {
    public PdfProcessingException() { super(); }
    public PdfProcessingException(String message) { super(message); }
    public PdfProcessingException(String message, Throwable cause) { super(message, cause); }
}