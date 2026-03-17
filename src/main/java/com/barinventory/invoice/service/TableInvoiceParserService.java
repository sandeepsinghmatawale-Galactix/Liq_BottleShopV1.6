package com.barinventory.invoice.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.barinventory.invoice.dto.ExtractedItemData;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TableInvoiceParserService {

    public List<ExtractedItemData> parseTable(List<List<String>> rows) {

        List<ExtractedItemData> items = new ArrayList<>();

        if (rows == null || rows.isEmpty()) {
            return items;
        }

        for (List<String> row : rows) {

            try {
                // Skip invalid rows
                if (row.size() < 6) continue;

                // Example mapping (adjust based on your PDF format)
                String brandRaw = getSafe(row, 0);
                String sizeText = getSafe(row, 1);   // e.g. "650x12"
                String casesText = getSafe(row, 2);
                String rateCaseText = getSafe(row, 3);
                String mrpText = getSafe(row, 4);
                String totalText = getSafe(row, 5);

                // Parse size + bottles
                Integer sizeMl = extractSize(sizeText);
                Integer bottles = extractBottles(sizeText);

                ExtractedItemData item = ExtractedItemData.builder()
                        .brandNameRaw(brandRaw)
                        .sizeMl(sizeMl)
                        .bottlesPerCase(bottles)
                        .invoicedCases(parseInt(casesText))
                        .ratePerCase(parseDouble(rateCaseText))
                        .mrpPerBottle(parseDouble(mrpText))
                        .lineTotal(parseDouble(totalText))
                        .build();

                // Calculate totals
                item.calculateTotals();

                items.add(item);

            } catch (Exception e) {
                log.warn("Failed to parse row: {}", row, e);
            }
        }

        return items;
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String getSafe(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }

    private Integer parseInt(String val) {
        try {
            return val == null || val.isEmpty() ? null : Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String val) {
        try {
            return val == null || val.isEmpty() ? null : Double.parseDouble(val.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractSize(String text) {
        // "650x12" → 650
        try {
            if (text != null && text.contains("x")) {
                return Integer.parseInt(text.split("x")[0].trim());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Integer extractBottles(String text) {
        // "650x12" → 12
        try {
            if (text != null && text.contains("x")) {
                return Integer.parseInt(text.split("x")[1].trim());
            }
        } catch (Exception ignored) {}
        return null;
    }
}