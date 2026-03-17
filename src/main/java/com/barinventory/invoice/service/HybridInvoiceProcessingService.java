package com.barinventory.invoice.service;

import java.io.File;
import java.util.List;

import org.springframework.stereotype.Service;

import com.barinventory.invoice.dto.ExtractedInvoiceData;
import com.barinventory.invoice.dto.ExtractedItemData;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HybridInvoiceProcessingService {

    private final TableExtractionService tableService;
    private final TableInvoiceParserService tableParser;
    private final ICDCInvoiceParserService regexParser;

    public ExtractedInvoiceData process(File pdfFile, String rawText) {

    
        List<List<String>> tableRows = tableService.extractTable(pdfFile);
        List<ExtractedItemData> tableItems = tableParser.parseTable(tableRows);

        // 2. Regex extraction
        ExtractedInvoiceData regexData = regexParser.parse(rawText);

        // 3. Smart decision
        if (isTableReliable(tableItems)) {
            return merge(regexData, tableItems);
        }

        return regexData;
    }

    private boolean isTableReliable(List<ExtractedItemData> items) {

        if (items == null || items.isEmpty()) return false;

        long validItems = items.stream()
                .filter(i ->
                        i.getBrandNameRaw() != null &&
                        i.getInvoicedCases() != null &&
                        i.getInvoicedCases() > 0
                )
                .count();

        double score = (double) validItems / items.size();

        return score >= 0.7; // 70% confidence
    }
    
    private ExtractedInvoiceData buildFromTable(
            List<ExtractedItemData> tableItems,
            ExtractedInvoiceData regexData) {

        // Use table items
        regexData.setItems(tableItems);
        regexData.setTotalLinesExtracted(tableItems.size());

        // 🔥 Smart fallback for header fields
        if (regexData.getInvoiceNumber() == null) {
            regexData.setInvoiceNumber("UNKNOWN");
        }

        if (regexData.getInvoiceDate() == null) {
            // leave null or handle default
        }

        // Recalculate totals (IMPORTANT)
        double total = tableItems.stream()
                .mapToDouble(i -> i.getLineTotal() != null ? i.getLineTotal() : 0)
                .sum();

        regexData.setTotalAmount(total);

        return regexData;
    }
    private ExtractedInvoiceData merge(
            ExtractedInvoiceData regexData,
            List<ExtractedItemData> tableItems) {

        if (tableItems == null || tableItems.isEmpty()) {
            return regexData;
        }

        List<ExtractedItemData> finalItems;

        if (tableItems.size() >= regexData.getItems().size()) {
            finalItems = tableItems;
        } else {
            finalItems = regexData.getItems();
        }

        regexData.setItems(finalItems);
        regexData.setTotalLinesExtracted(finalItems.size());

        return regexData;
    }
}