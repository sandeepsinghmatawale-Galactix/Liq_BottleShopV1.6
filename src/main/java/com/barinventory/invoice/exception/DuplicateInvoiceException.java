package com.barinventory.invoice.exception;

 
public class DuplicateInvoiceException extends RuntimeException {
    public DuplicateInvoiceException() { super(); }
    public DuplicateInvoiceException(String message) { super(message); }
}