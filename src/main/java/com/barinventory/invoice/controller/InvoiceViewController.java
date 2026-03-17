package com.barinventory.invoice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.barinventory.invoice.service.InvoiceService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/invoices")   // ✅ BASE PATH
public class InvoiceViewController {

    private final InvoiceService invoiceService;

    // ✅ /invoices
    @GetMapping
    public String invoiceList(Model model) {
        model.addAttribute("activePage", "invoices"); // ⭐ important for UI
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        
        model.addAttribute("pendingCount", invoiceService.getPendingInvoices().size());
        return "invoices/invoice-list";
    }

    // ✅ /invoices/upload
    @GetMapping("/upload")
    public String uploadScreen(Model model) {
        model.addAttribute("activePage", "invoices");
        return "invoices/invoice-upload";
    }

    // ✅ /invoices/{id}/review
    @GetMapping("/{id}/review")
    public String reviewScreen(@PathVariable Long id, Model model) {
        model.addAttribute("activePage", "invoices");
        model.addAttribute("invoiceId", id);
        return "invoices/invoice-review";
    }

    // ✅ /invoices/{id}/detail
    @GetMapping("/{id}/detail")
    public String invoiceDetail(@PathVariable Long id, Model model) {
        model.addAttribute("activePage", "invoices");
        model.addAttribute("invoice", invoiceService.getInvoiceById(id));
        return "invoices/invoice-detail";
    }
}