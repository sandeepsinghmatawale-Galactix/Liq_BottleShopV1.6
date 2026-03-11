package com.barinventory.billing.service;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.barinventory.billing.dtos.BillResponse;
import com.barinventory.billing.dtos.BillResponse.BillItemResponse;
import com.barinventory.billing.entity.Bill;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillPdfService {

    private final TemplateEngine templateEngine;

    public byte[] generatePdf(Bill bill) throws Exception {

        // Map entity → DTO
        BillResponse dto = BillResponse.builder()
            .billId(bill.getId())
            .createdBy(bill.getUser() != null ? bill.getUser().getName() : "Unknown")
            .createdAt(bill.getCreatedAt())
            .grandTotal(bill.getGrandTotal())
            .finalized(bill.isFinalized())
            .items(bill.getItems().stream()
                .map(item -> BillItemResponse.builder()
                    .brandId(item.getBrandId())
                    .brandName(item.getBrandName())
                    .sizeLabel(item.getSizeLabel())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .lineTotal(item.getLineTotal())
                    .build())
                .collect(Collectors.toList()))
            .build();

        // ✅ Use plain Context (not WebContext) — bill-pdf.html has NO @{} links
        Context context = new Context();
        context.setVariable("bill", dto);

        // ✅ Point to the dedicated PDF template, NOT bill-detail
        String html = templateEngine.process("billing/bill-pdf", context);

        // Render to PDF bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(out);

        return out.toByteArray();
    }
}