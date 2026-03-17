package com.barinventory.invoice.exception;

public class InvalidPdfException extends RuntimeException {
    public InvalidPdfException() { super(); }
    public InvalidPdfException(String message) { super(message); }
}
