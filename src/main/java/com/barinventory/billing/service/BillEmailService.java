package com.barinventory.billing.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.barinventory.billing.entity.Bill;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class BillEmailService {

    private final JavaMailSender mailSender;
    private final BillPdfService pdfService;

    public void sendBillByEmail(Bill bill, String toEmail) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Your Bill #" + bill.getId());
        helper.setText("<p>Please find your bill attached.</p>", true);

        // attach PDF
        byte[] pdf = pdfService.generatePdf(bill);
        helper.addAttachment("Bill-" + bill.getId() + ".pdf",
            new ByteArrayResource(pdf), "application/pdf");

        mailSender.send(message);
    }
}