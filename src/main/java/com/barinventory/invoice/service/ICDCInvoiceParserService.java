package com.barinventory.invoice.service;

import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

import com.barinventory.invoice.dto.ExtractedInvoiceData;
import com.barinventory.invoice.dto.ExtractedItemData;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ICDCInvoiceParserService implements InvoiceParserService {

    // Flexible date pattern (if needed for invoice date parsing)
    private static final Pattern INVOICE_NUMBER_PATTERN = Pattern.compile("ICDC\\d{10,}");

    @Override
    public ExtractedInvoiceData parse(String rawText) {
        ExtractedInvoiceData result = new ExtractedInvoiceData();
        try {
            result.setInvoiceNumber(extractInvoiceNumber(rawText));
            result.setItems(extractItemsFromBlocks(rawText));
            result.setExtractionSuccess(true);
        } catch (Exception e) {
            log.error("Invoice parse failed", e);
            result.setExtractionSuccess(false);
        }
        return result;
    }

    /**
     * Extract invoice items from raw text
     */
    private List<ExtractedItemData> extractItemsFromBlocks(String text) {
        List<ExtractedItemData> items = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue; // skip empty lines

            // Skip lines with no digits at all
            if (!line.matches(".*\\d.*")) continue;

            try {
                ExtractedItemData item = new ExtractedItemData();

                // Combine multi-line blocks until empty line or total/summary line
                StringBuilder block = new StringBuilder(line);
                int j = i + 1;
                while (j < lines.length && !lines[j].trim().isEmpty() && !lines[j].trim().toLowerCase().startsWith("total")) {
                    block.append(" ").append(lines[j].trim());
                    j++;
                }
                String full = block.toString();

                log.debug("Parsing block: {}", full);

                // 1️⃣ Extract brand
                String brand = full.replaceFirst("^\\d+\\s+\\d+\\s+", "");
                brand = brand.split("(?i)(Beer|IML)")[0].trim();
                item.setBrandNameRaw(brand);

                // 2️⃣ Extract size in ml
             // 2️⃣ Extract size in ml
                Matcher sizeM = Pattern.compile("(\\d{2,4})\\s*ml", Pattern.CASE_INSENSITIVE).matcher(full);
                if (sizeM.find()) {
                    item.setSizeMl(Integer.parseInt(sizeM.group(1)));
                } else {
                    log.warn("No size_ml found for brand '{}', setting default 0", item.getBrandNameRaw());
                    item.setSizeMl(0); // default value to prevent DB errors
                }

                // 3️⃣ Extract bottles per case & invoiced cases
                Matcher numbersM = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(full);
                if (numbersM.find()) {
                    item.setBottlesPerCase(Integer.parseInt(numbersM.group(1)));
                    item.setInvoicedCases(Integer.parseInt(numbersM.group(2)));
                } else {
                    item.setBottlesPerCase(0);
                    item.setInvoicedCases(0);
                }

                // 4️⃣ Extract prices (rate per case, MRP per bottle, line total)
                Matcher priceM = Pattern.compile("(\\d+[\\d,]*\\.\\d{2})").matcher(full);
                List<Double> prices = new ArrayList<>();
                while (priceM.find()) {
                    prices.add(Double.parseDouble(priceM.group(1).replace(",", "")));
                }
                if (prices.size() >= 3) {
                    item.setRatePerCase(prices.get(prices.size() - 3));
                    item.setMrpPerBottle(prices.get(prices.size() - 2));
                    item.setLineTotal(prices.get(prices.size() - 1));
                } else {
                    item.setRatePerCase(0.0);
                    item.setMrpPerBottle(0.0);
                    item.setLineTotal(0.0);
                }

                // 5️⃣ Compute totals (optional helper)
                item.calculateTotals();

                items.add(item);

                // Skip processed lines
                i = j - 1;

            } catch (Exception e) {
                log.warn("Parse error at line {}: {}", i, line);
            }
        }

        log.info("Total items extracted: {}", items.size());
        return items;
    }

    /**
     * Extract invoice number from text
     */
    private String extractInvoiceNumber(String text) {
        Matcher matcher = INVOICE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}