package com.barinventory.invoice.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.barinventory.invoice.dto.*;
import com.barinventory.invoice.service.InvoiceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ExtractionResultResponse>> upload(
            @RequestParam MultipartFile file,
            @RequestParam String uploadedBy) {

        return ResponseEntity.ok(
                ApiResponse.success("Uploaded",
                        invoiceService.uploadAndExtract(file, uploadedBy, null))
        );
    }

    @PostMapping("/upload/bulk")
    public ResponseEntity<ApiResponse<List<ExtractionResultResponse>>> bulk(
            @RequestParam List<MultipartFile> files,
            @RequestParam String uploadedBy) {

        return ResponseEntity.ok(
                ApiResponse.success("Bulk done",
                        invoiceService.bulkUploadAndExtract(files, uploadedBy, null))
        );
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<String>> confirm(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate stockDate,
            @RequestParam String confirmedBy) {

        invoiceService.confirmInvoice(id, stockDate, confirmedBy);

        return ResponseEntity.ok(ApiResponse.success("Confirmed", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceSummaryResponse>>> all() {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getAllInvoices()));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<InvoiceSummaryResponse>>> pending() {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getPendingInvoices()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> byId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoiceById(id)));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<InvoiceSummaryResponse>>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(
                ApiResponse.success(invoiceService.getInvoicesByDateRange(from, to))
        );
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(invoiceService.getInvoicePdf(id));
    }
}