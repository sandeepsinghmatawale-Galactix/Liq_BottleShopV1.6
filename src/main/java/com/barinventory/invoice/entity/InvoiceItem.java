package com.barinventory.invoice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // ── Brand Reference ───────────────────────────────────────────────────────   
    
    @Column(name = "brand_name_raw", length = 500, nullable = false)
    private String brandNameRaw;

    @Column(name = "brand_name_matched", length = 200)
    private String brandNameMatched;

    @Column(name = "brand_master_id")
    private Long brandMasterId;

    // ── Product Info from ICDC ────────────────────────────────────────────────

    /** "Beer" or "IML" */
    @Column(name = "product_type", length = 10)
    private String productType;

    /** "G" (Glass) or "P" (PET) */
    @Column(name = "pack_type", length = 5)
    private String packType;

    // ── Size & Pack ───────────────────────────────────────────────────────────

    @Column(name = "size_ml")
    private Integer sizeMl;

    @Column(name = "bottles_per_case", nullable = false)
    @Builder.Default
    private Integer bottlesPerCase = 12;

    // ── Invoiced Quantities ───────────────────────────────────────────────────

    @Column(name = "invoiced_cases", nullable = false)
    private Integer invoicedCases;

    @Column(name = "invoiced_bottles")
    private Integer invoicedBottles;

    // ── Received Quantities (entered by owner) ────────────────────────────────

    @Column(name = "received_cases")
    @Builder.Default
    private Integer receivedCases = 0;

    @Column(name = "received_bottles")
    private Integer receivedBottles;

    @Column(name = "breakage_qty")
    @Builder.Default
    private Integer breakageQty = 0;

    @Column(name = "shortage_cases")
    private Integer shortageCases;

    // ── Pricing ───────────────────────────────────────────────────────────────

    @Column(name = "mrp_per_bottle")
    private Double mrpPerBottle;

    @Column(name = "rate_per_case")
    private Double ratePerCase;

    @Column(name = "line_total")
    private Double lineTotal;

    // ── Stockroom Flag ────────────────────────────────────────────────────────

    @Column(name = "posted_to_stockroom")
    @Builder.Default
    private Boolean postedToStockroom = false;

    // ── Calculated Fields ─────────────────────────────────────────────────────

    public void calculateDerivedFields() {
        if (invoicedCases != null && bottlesPerCase != null) {
            this.invoicedBottles = invoicedCases * bottlesPerCase;
        }
        if (receivedCases != null && bottlesPerCase != null) {
            int gross = receivedCases * bottlesPerCase;
            int breakage = breakageQty != null ? breakageQty : 0;
            this.receivedBottles = Math.max(0, gross - breakage);
        }
        if (invoicedCases != null && receivedCases != null) {
            this.shortageCases = Math.max(0, invoicedCases - receivedCases);
        }
    }

    public boolean hasShortage() {
        return shortageCases != null && shortageCases > 0;
    }

    public boolean hasBreakage() {
        return breakageQty != null && breakageQty > 0;
    }

	public void setMatchConfident(boolean matchConfident) {
		// TODO Auto-generated method stub
		
	}
}