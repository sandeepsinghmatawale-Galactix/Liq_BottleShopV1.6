package com.barinventory.invoice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.barinventory.invoice.entity.Invoice;
import com.barinventory.invoice.entity.InvoiceStatus;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	// ── Used in InvoiceService.uploadAndExtract() ─────────────────────────────

	boolean existsByInvoiceNumber(String invoiceNumber);

	// ── Used in confirmInvoice() and getInvoiceById() ─────────────────────────
	// JOIN FETCH avoids N+1 — loads items in single query

	@Query("SELECT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.id = :id")
	Optional<Invoice> findByIdWithItems(@Param("id") Long id);

	// ── Used in getAllInvoices() ───────────────────────────────────────────────

	@Query("SELECT i FROM Invoice i ORDER BY i.stockReceivedDate DESC NULLS LAST")
	List<Invoice> findAllByOrderByStockReceivedDateDesc();

	// ── Used in getPendingInvoices() ──────────────────────────────────────────

	@Query("SELECT i FROM Invoice i WHERE i.status = 'PENDING' ORDER BY i.uploadedAt DESC")
	List<Invoice> findAllPendingInvoices();

	// ── Used in getInvoicesByDateRange() ──────────────────────────────────────

	List<Invoice> findByStockReceivedDateBetweenOrderByStockReceivedDateDesc(LocalDate from, LocalDate to);

	// ── Extra — useful for future reporting ───────────────────────────────────

	List<Invoice> findByStatus(InvoiceStatus status);

	List<Invoice> findByInvoiceDateOrderByInvoiceDateDesc(LocalDate invoiceDate);

	@Query("SELECT i FROM Invoice i WHERE i.uploadedBy = :user ORDER BY i.uploadedAt DESC")
	List<Invoice> findByUploadedBy(@Param("user") String uploadedBy);
	
	  List<Invoice> findByInvoiceDateBetween(LocalDate from, LocalDate to);
	
	
}