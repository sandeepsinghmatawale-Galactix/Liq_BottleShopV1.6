package com.barinventory.invoice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// ── Invoice Identity ──────────────────────────────────────────────────────

	@Column(name = "invoice_number", nullable = false, unique = true, length = 100)
	private String invoiceNumber;

	@Column(name = "depot_name", length = 200)
	private String depotName;

	@Column(name = "depot_code", length = 50)
	private String depotCode;

	// ── Retailer info from ICDC header ────────────────────────────────────────

	@Column(name = "retailer_name", length = 200)
	private String retailerName;

	@Column(name = "retailer_code", length = 50)
	private String retailerCode;

	@Column(name = "license_number", length = 100)
	private String licenseNumber;

	// ── Three Critical Dates ──────────────────────────────────────────────────

	@Column(name = "invoice_date")
	private LocalDate invoiceDate; // From PDF — govt depot bill date

	@Column(name = "stock_received_date")
	private LocalDate stockReceivedDate; // Owner entered — actual arrival date

	@CreationTimestamp
	@Column(name = "uploaded_at", updatable = false)
	private LocalDateTime uploadedAt; // System auto — audit only

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// ── Financials ────────────────────────────────────────────────────────────

	@Column(name = "total_amount")
	private Double totalAmount;

	@Column(name = "vehicle_charges")
	private Double vehicleCharges;

	@Column(name = "vehicle_number", length = 20)
	private String vehicleNumber;

	// ── PDF Storage ───────────────────────────────────────────────────────────

	@Column(name = "pdf_file_name", length = 255)
	private String pdfFileName;

	@Column(name = "pdf_file_path", length = 500)
	private String pdfFilePath;

	// ── Status ────────────────────────────────────────────────────────────────

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	@Builder.Default
	private InvoiceStatus status = InvoiceStatus.PENDING;

	@Column(name = "remarks", length = 500)
	private String remarks;

	// ── Audit ─────────────────────────────────────────────────────────────────

	@Column(name = "uploaded_by", length = 100)
	private String uploadedBy;

	@Column(name = "confirmed_by", length = 100)
	private String confirmedBy;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	// ── ICDC footer summary ───────────────────────────────────────────────────

	@Column(name = "summary_total_cases")
	private Integer summaryTotalCases;

	@Column(name = "summary_breakage_cases")
	private Integer summaryBreakageCases;

	@Column(name = "summary_shortage_cases")
	private Integer summaryShortageCases;

	// ── Line Items ────────────────────────────────────────────────────────────

	@OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<InvoiceItem> items = new ArrayList<>();

	// ── Helpers ───────────────────────────────────────────────────────────────

	public void addItem(InvoiceItem item) {
		items.add(item);
		item.setInvoice(this);
	}

	public void removeItem(InvoiceItem item) {
		items.remove(item);
		item.setInvoice(null);
	}

	public boolean hasDiscrepancy() {
		if (items == null || items.isEmpty())
			return false;
		return items.stream().anyMatch(item -> item.hasShortage() || item.hasBreakage());
	}
}