package com.barinventory.invoice.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException() { super(); }
    public InvoiceNotFoundException(String message) { super(message); }
}