package com.barinventory.invoice.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.barinventory.invoice.dto.ExtractedInvoiceData;
import com.barinventory.invoice.dto.ExtractedItemData;
import com.barinventory.invoice.dto.ExtractionResultResponse;
import com.barinventory.invoice.dto.InvoiceResponse;
import com.barinventory.invoice.dto.InvoiceSummaryResponse;
import com.barinventory.invoice.entity.Invoice;
import com.barinventory.invoice.entity.InvoiceItem;
import com.barinventory.invoice.entity.InvoiceStatus;
import com.barinventory.invoice.exception.DuplicateInvoiceException;
import com.barinventory.invoice.exception.ExtractionFailedException;
import com.barinventory.invoice.exception.InvalidPdfException;
import com.barinventory.invoice.exception.PdfProcessingException;
import com.barinventory.invoice.port.StockroomPort;
import com.barinventory.invoice.repository.InvoiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InvoiceService {

    private final PDFBoxExtractorService pdfBoxExtractorService;
    private final HybridInvoiceProcessingService hybridService;
    private final BrandMatcherService brandMatcherService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceFileStorageService fileStorageService;
    private final StockroomPort stockroomPort;

    // ─────────────────────────────────────────────
    // ✅ UPLOAD & EXTRACT
    // ─────────────────────────────────────────────

    // ─────────────────────────────────────────────
    // ✅ BULK UPLOAD
    // ─────────────────────────────────────────────
    public List<ExtractionResultResponse> bulkUploadAndExtract(List<MultipartFile> files, String uploadedBy,
            List<BrandMatcherService.BrandMasterRef> masterBrands) {

        return files.stream().map(file -> uploadAndExtract(file, uploadedBy, masterBrands)).toList();
    }

    // ─────────────────────────────────────────────
    // ✅ CONFIRM + STOCK POST
    // ─────────────────────────────────────────────

    // ─────────────────────────────────────────────
    private void postInvoiceToStock(Invoice invoice) {

        invoice.getItems().forEach(item -> {

            Integer bottles = item.getInvoicedBottles();

            stockroomPort.addStock(item.getBrandMasterId(), item.getBrandNameMatched(), item.getSizeMl(), bottles,
                    invoice.getStockReceivedDate(), "INVOICE:" + invoice.getInvoiceNumber());
        });
    }

    // ─────────────────────────────────────────────
    // ✅ FETCH METHODS
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream().map(this::mapToSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> getPendingInvoices() {
        return invoiceRepository.findByStatus(InvoiceStatus.PENDING).stream().map(this::mapToSummary).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        return mapToResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> getInvoicesByDateRange(LocalDate from, LocalDate to) {
        return invoiceRepository.findByInvoiceDateBetween(from, to).stream().map(this::mapToSummary).toList();
    }

    @Transactional(readOnly = true)
    public byte[] getInvoicePdf(Long id) {
        try {
            Invoice invoice = getOrThrow(id);
            return Files.readAllBytes(new File(invoice.getPdfFilePath()).toPath());
        } catch (IOException e) {
            throw new RuntimeException("PDF read failed");
        }
    }
    
    public Invoice saveInvoice(ExtractedInvoiceData extractedInvoiceData) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(extractedInvoiceData.getInvoiceNumber());

        // Initialize list if null
        if (invoice.getItems() == null) {
            invoice.setItems(new ArrayList<>());
        }

        List<ExtractedItemData> extractedItems = extractedInvoiceData.getItems();
        if (extractedItems != null && !extractedItems.isEmpty()) {
            for (ExtractedItemData eItem : extractedItems) {
                InvoiceItem invoiceItem = new InvoiceItem();
                invoiceItem.setBrandNameRaw(eItem.getBrandNameRaw());
                invoiceItem.setSizeMl(eItem.getSizeMl());
                invoiceItem.setBottlesPerCase(eItem.getBottlesPerCase());
                invoiceItem.setInvoicedCases(eItem.getInvoicedCases());
                invoiceItem.setRatePerCase(eItem.getRatePerCase());
                invoiceItem.setMrpPerBottle(eItem.getMrpPerBottle());
                invoiceItem.setLineTotal(eItem.getLineTotal());

                invoice.addItem(invoiceItem); // safe, adds to initialized list
            }
        }

        return invoiceRepository.save(invoice);
    }

    // ─────────────────────────────────────────────
    // ✅ HELPERS
    // ─────────────────────────────────────────────

    private InvoiceSummaryResponse mapToSummary(Invoice i) {

        int totalItems = i.getItems() != null ? i.getItems().size() : 0;

        int totalCases = (i.getItems() != null) ? i.getItems().stream()
                .map(item -> item.getInvoicedCases() != null ? item.getInvoicedCases() : 0).reduce(0, Integer::sum) : 0;

        return InvoiceSummaryResponse.builder().id(i.getId()).invoiceNumber(i.getInvoiceNumber())
                .depotName(i.getDepotName()).invoiceDate(i.getInvoiceDate()).stockReceivedDate(i.getStockReceivedDate())
                .status(i.getStatus()).totalItems(totalItems).totalCases(totalCases).hasDiscrepancy(i.hasDiscrepancy())
                .uploadedAt(i.getUploadedAt()).build();
    }

    private InvoiceResponse mapToResponse(Invoice i) {
        return InvoiceResponse.builder().id(i.getId()).invoiceNumber(i.getInvoiceNumber())
                .invoiceDate(i.getInvoiceDate()).stockReceivedDate(i.getStockReceivedDate()).status(i.getStatus())
                .totalAmount(i.getTotalAmount()).build();
    }

    @Transactional
    public ExtractionResultResponse uploadAndExtract(MultipartFile file, String uploadedBy,
            List<BrandMatcherService.BrandMasterRef> masterBrands) {

        // 1️⃣ Validate PDF
        if (!pdfBoxExtractorService.isValidPDF(file)) {
            log.warn("Invalid PDF uploaded: {}", file.getOriginalFilename());
            throw new InvalidPdfException("Invalid PDF file: " + file.getOriginalFilename());
        }

        // 2️⃣ Store PDF
        String storedPath = fileStorageService.storeFile(file);
        log.info("PDF stored at path: {}", storedPath);

        // 3️⃣ Extract text
        String rawText;
        try {
            rawText = pdfBoxExtractorService.extractText(file);
        } catch (IOException e) {
            fileStorageService.deleteFile(storedPath);
            log.error("PDF extraction failed for file: {}", file.getOriginalFilename(), e);
            throw new PdfProcessingException("Failed to extract text from PDF: " + file.getOriginalFilename());
        }

        // 4️⃣ Convert to temp file for hybrid processing
        File tempPdf = convertToFile(file);
        ExtractedInvoiceData extracted;
        try {
            extracted = hybridService.process(tempPdf, rawText);
        } finally {
            if (tempPdf.exists()) tempPdf.delete();
        }

        if (extracted == null || extracted.getItems().isEmpty()) {
            fileStorageService.deleteFile(storedPath);
            log.warn("No items extracted from PDF: {}", file.getOriginalFilename());
            throw new ExtractionFailedException("No invoice items extracted: " + file.getOriginalFilename());
        }

        // 5️⃣ Load master brands if empty
        if (masterBrands == null || masterBrands.isEmpty()) {
            masterBrands = brandMatcherService.getAllBrandRefs();
        }

        // 6️⃣ Check duplicates
        if (invoiceRepository.existsByInvoiceNumber(extracted.getInvoiceNumber())) {
            fileStorageService.deleteFile(storedPath);
            log.warn("Duplicate invoice detected: {}", extracted.getInvoiceNumber());
            throw new DuplicateInvoiceException("Invoice already exists: " + extracted.getInvoiceNumber());
        }

        // 7️⃣ Save invoice
        Invoice savedInvoice = saveInvoice(extracted, storedPath, file.getOriginalFilename(), uploadedBy);

        log.info("Invoice uploaded successfully: {} with ID {}", extracted.getInvoiceNumber(), savedInvoice.getId());

        // 8️⃣ Build response
        return ExtractionResultResponse.builder()
                .invoiceId(savedInvoice.getId())
                .invoiceNumber(extracted.getInvoiceNumber())
                .invoiceDate(extracted.getInvoiceDate())
                .totalAmount(extracted.getTotalAmount())
                .extractionSuccess(true)
                .build();
    }

    // ✅ SAVE METHOD — null-safe invoice number
    private Invoice saveInvoice(ExtractedInvoiceData data, String filePath, String fileName, String uploadedBy) {

        // Null-safe invoice number
        String invoiceNumber = (data.getInvoiceNumber() != null && !data.getInvoiceNumber().isBlank())
                ? data.getInvoiceNumber()
                : "TEMP-" + System.currentTimeMillis();

        // Build the invoice entity
        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .invoiceDate(data.getInvoiceDate())
                .depotName(data.getDepotName())
                .retailerName(data.getRetailerName())
                .totalAmount(data.getTotalAmount() != null ? data.getTotalAmount() : 0.0)
                .uploadedBy(uploadedBy)
                .pdfFileName(fileName)
                .pdfFilePath(filePath)
                .status(InvoiceStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();

        // Ensure items list is initialized
        if (data.getItems() != null) {
            data.getItems().forEach(item -> {
                // Null-safe defaults
                item.setBottlesPerCase(item.getBottlesPerCase() != null ? item.getBottlesPerCase() : 0);
                item.setInvoicedCases(item.getInvoicedCases() != null ? item.getInvoicedCases() : 0);
                item.setInvoicedBottles(item.getInvoicedBottles() != null ? item.getInvoicedBottles() : 0);
                item.setMrpPerBottle(item.getMrpPerBottle() != null ? item.getMrpPerBottle() : 0.0);
                item.setRatePerCase(item.getRatePerCase() != null ? item.getRatePerCase() : 0.0);
                item.setLineTotal(item.getLineTotal() != null ? item.getLineTotal() : 0.0);

                InvoiceItem entity = new InvoiceItem();

                // ✅ Truncate brandNameRaw to max 200 characters
                String rawName = item.getBrandNameRaw();
                if (rawName != null && rawName.length() > 499) {
                    rawName = rawName.substring(0, 499);
                }
                entity.setBrandNameRaw(rawName);

                entity.setSizeMl(item.getSizeMl());
                entity.setBottlesPerCase(item.getBottlesPerCase());
                entity.setInvoicedCases(item.getInvoicedCases());
                entity.setInvoicedBottles(item.getInvoicedBottles());
                entity.setMrpPerBottle(item.getMrpPerBottle());
                entity.setRatePerCase(item.getRatePerCase());
                entity.setLineTotal(item.getLineTotal());
                entity.setBrandNameMatched(item.getBrandNameMatched());
                entity.setBrandMasterId(item.getBrandMasterId());
                entity.setMatchConfident(item.isMatchConfident());

                // Add item to invoice
                invoice.addItem(entity);
            });
        }

        // Save invoice along with items
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice saved with ID {} containing {} items", saved.getId(),
                saved.getItems() != null ? saved.getItems().size() : 0);
        return saved;
    }
    // ✅ CONFIRM
    public void confirmInvoice(Long id, LocalDate stockDate, String confirmedBy) {

        Invoice invoice = getOrThrow(id);

        if (invoice.getStatus() == InvoiceStatus.CONFIRMED) {
            throw new RuntimeException("Already confirmed");
        }

        invoice.setStockReceivedDate(stockDate);
        invoice.setConfirmedBy(confirmedBy);
        invoice.setConfirmedAt(LocalDateTime.now());

        invoice.getItems().forEach(item -> {
            stockroomPort.addStock(item.getBrandMasterId(), item.getBrandNameMatched(), item.getSizeMl(),
                    item.getInvoicedBottles(), stockDate, "INV:" + invoice.getInvoiceNumber());
        });

        invoice.setStatus(InvoiceStatus.CONFIRMED);
        invoiceRepository.save(invoice);
    }

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    private File convertToFile(MultipartFile file) {
        try {
            File temp = File.createTempFile("inv", ".pdf");
            file.transferTo(temp);
            return temp;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ExtractionResultResponse buildExtractionResponse(Invoice invoice, ExtractedInvoiceData extracted) {

        return ExtractionResultResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(extracted.getInvoiceNumber())
                .invoiceDate(extracted.getInvoiceDate())
                .totalAmount(extracted.getTotalAmount())
                .extractionSuccess(true)
                .build();
    }
}