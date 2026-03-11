package com.barinventory.billing.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.barinventory.billing.dtos.BillResponse;
import com.barinventory.billing.dtos.CreateBillRequest;
import com.barinventory.billing.entity.Bill;
import com.barinventory.billing.service.BillEmailService;
import com.barinventory.billing.service.BillPdfService;
import com.barinventory.billing.service.BillingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/billing/bills")   // ✅ base = /billing/bills
@RequiredArgsConstructor
public class BillController {

    private final BillingService billingService;
    private final BillPdfService billPdfService;
    private final BillEmailService emailService;

    /**
     * GET /billing/bills
     */
    @GetMapping
    public String billListPage(@AuthenticationPrincipal UserDetails userDetails,
                               Model model) {

        List<BillResponse> bills =
                billingService.getBillsForUser(userDetails.getUsername());

        BigDecimal totalSpent = bills.stream()
                .map(BillResponse::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("bills", bills);
        model.addAttribute("totalSpent", totalSpent);

        return "billing/bill-list";
    }

    /**
     * GET /billing/bills/new
     */
    @GetMapping("/new")
    public String newBillForm(Model model) {
        model.addAttribute("brands", billingService.getAllActiveBrands2());
        model.addAttribute("billRequest", new CreateBillRequest());
        return "billing/bill-create";
    }

    /**
     * POST /billing/bills/new
     */
    @PostMapping("/new")
    public String saveBill(@Valid @ModelAttribute("billRequest") CreateBillRequest request,
                           BindingResult result,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model,
                           RedirectAttributes ra) {

        if (result.hasErrors()
                || request.getItems() == null
                || request.getItems().isEmpty()) {

            model.addAttribute("brands", billingService.getAllActiveBrands2());
            model.addAttribute("errorMsg", "Add at least one item to the bill.");
            return "billing/bill-create";
        }

        try {
            BillResponse bill =
                    billingService.createBill(request, userDetails.getUsername());

            ra.addFlashAttribute("successMsg",
                    "Bill #" + bill.getBillId()
                            + " saved. Total: ₹" + bill.getGrandTotal());

            return "redirect:/billing/bills/" + bill.getBillId();

        } catch (Exception e) {
            model.addAttribute("brands", billingService.getAllActiveBrands2());
            model.addAttribute("errorMsg", e.getMessage());
            return "billing/bill-create";
        }
    }

    /**
     * GET /billing/bills/{id}
     */
    @GetMapping("/{id}")
    public String billDetailPage(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 Model model) {

        model.addAttribute("bill",
                billingService.getBillById(id, userDetails.getUsername()));

        return "billing/bill-detail";
    }

    /**
     * GET /billing/bills/{id}/pdf     ✅ just /{id}/pdf, NOT /billing/bills/{id}/pdf
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable Long id) {
        try {
            Bill bill = billingService.findById(id);
            byte[] pdf = billPdfService.generatePdf(bill);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Bill-" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception e) {
            log.error("PDF generation failed for bill {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /billing/bills/{id}/email   ✅ removed duplicate stub, kept real implementation
     */
    @GetMapping("/{id}/email")
    public String emailBill(@PathVariable Long id,
                            @RequestParam(defaultValue = "") String to,
                            RedirectAttributes ra) {
        if (to.isBlank()) {
            ra.addFlashAttribute("error", "Please enter an email address.");
            return "redirect:/billing/bills/" + id;
        }
        try {
            Bill bill = billingService.findById(id);
            emailService.sendBillByEmail(bill, to);
            ra.addFlashAttribute("success", "Bill emailed to " + to);
        } catch (Exception e) {
            log.error("Email failed for bill {}", id, e);
            ra.addFlashAttribute("error", "Email failed: " + e.getMessage());
        }
        return "redirect:/billing/bills/" + id;
    }
}