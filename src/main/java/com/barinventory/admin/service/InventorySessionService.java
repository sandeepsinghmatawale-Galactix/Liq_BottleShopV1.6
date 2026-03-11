package com.barinventory.admin.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barinventory.admin.config.WellConfig;
import com.barinventory.admin.entity.Bar;
import com.barinventory.admin.entity.BarProductPrice;
import com.barinventory.admin.entity.BarWell;
import com.barinventory.admin.entity.DistributionRecord;
import com.barinventory.admin.entity.InventorySession;
import com.barinventory.admin.entity.Product;
import com.barinventory.admin.entity.SalesRecord;
import com.barinventory.admin.entity.StockroomInventory;
import com.barinventory.admin.entity.WellInventory;
import com.barinventory.admin.enums.DistributionStatus;
import com.barinventory.admin.enums.SessionStatus;
import com.barinventory.admin.repository.BarProductPriceRepository;
import com.barinventory.admin.repository.BarRepository;
import com.barinventory.admin.repository.BarWellRepository;
import com.barinventory.admin.repository.DistributionRecordRepository;
import com.barinventory.admin.repository.InventorySessionRepository;
import com.barinventory.admin.repository.SalesRecordRepository;
import com.barinventory.admin.repository.StockroomInventoryRepository;
import com.barinventory.admin.repository.WellInventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventorySessionService {
    
    private final InventorySessionRepository sessionRepository;
    private final StockroomInventoryRepository stockroomRepository;
    private final DistributionRecordRepository distributionRepository;
    private final WellInventoryRepository wellRepository;
    private final SalesRecordRepository salesRepository;
    private final BarRepository barRepository;
    private final BarProductPriceRepository priceRepository;
    private final ProductService productService;
    private final BarWellRepository barWellRepository;
    
    /**
     * Initialize a new inventory session for a bar
     */
    @Transactional
    public InventorySession initializeSession(Long barId, String shiftType, String notes) {
        if (barId == null) throw new IllegalArgumentException("Bar ID cannot be null");

        Bar bar = barRepository.findById(barId)
            .orElseThrow(() -> new RuntimeException("Bar not found"));

        Optional<InventorySession> existingSession = sessionRepository
            .findFirstByBarBarIdAndStatusOrderBySessionStartTimeDesc(barId, SessionStatus.IN_PROGRESS);

        if (existingSession.isPresent()) {
            // ✅ FIX: use findByIdWithBar to ensure bar is loaded
            return sessionRepository.findByIdWithBar(existingSession.get().getSessionId())
                .orElseThrow(() -> new RuntimeException("Session not found"));
        }

        InventorySession session = InventorySession.builder()
            .bar(bar)
            .sessionStartTime(LocalDateTime.now())
            .status(SessionStatus.IN_PROGRESS)
            .shiftType(shiftType)
            .notes(notes)
            .stockroomInventories(new ArrayList<>())
            .distributionRecords(new ArrayList<>())
            .wellInventories(new ArrayList<>())
            .salesRecords(new ArrayList<>())
            .build();

        return sessionRepository.save(session);
    }
    
 /*   public InventorySession getSession(Long sessionId) {
        return sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));
    }*/
    
    
    
    public InventorySession getSession(Long sessionId) {
        return sessionRepository.findByIdWithBar(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
    }
    
    



 public Optional<InventorySession> getSessionById(Long sessionId) {
	    return sessionRepository.findByIdWithBar(sessionId);
	}

    
    /**
     * STAGE 1: Save stockroom inventory
     */
  
    
    @Transactional
    public void saveStockroomInventory(Long sessionId, List<StockroomInventory> inventories) {
        InventorySession session = getSessionInProgress(sessionId);

        // ✅ Delete existing stockroom records before re-saving to avoid duplicates
        stockroomRepository.deleteBySessionSessionId(sessionId);

        for (StockroomInventory inventory : inventories) {
            inventory.setSession(session);
            stockroomRepository.save(inventory);
        }

        log.info("Saved {} stockroom inventory records for session {}", 
            inventories.size(), sessionId);
    }
    
    /**
     * STAGE 2: Create distribution records from stockroom transferred quantities
     */
   
    
    @Transactional
    public void createDistributionRecords(Long sessionId) {
        InventorySession session = getSessionInProgress(sessionId);
        List<StockroomInventory> stockroomInventories = 
            stockroomRepository.findBySessionSessionId(sessionId);

        for (StockroomInventory stockroom : stockroomInventories) {
            if (stockroom.getTransferredOut().compareTo(BigDecimal.ZERO) > 0) {

                // ✅ Check if distribution record already exists for this session+product
                Optional<DistributionRecord> existing = distributionRepository
                    .findBySessionSessionIdAndProductProductId(
                        sessionId, stockroom.getProduct().getProductId());

                if (existing.isPresent()) {
                    // ✅ Update existing record instead of creating duplicate
                    DistributionRecord dr = existing.get();
                    dr.setQuantityFromStockroom(stockroom.getTransferredOut());
                    dr.setTotalAllocated(BigDecimal.ZERO);
                    dr.setUnallocated(stockroom.getTransferredOut());
                    dr.setStatus(DistributionStatus.PENDING_ALLOCATION);
                    distributionRepository.save(dr);
                } else {
                    // ✅ Create new only if doesn't exist
                    DistributionRecord distribution = DistributionRecord.builder()
                        .session(session)
                        .product(stockroom.getProduct())
                        .quantityFromStockroom(stockroom.getTransferredOut())
                        .totalAllocated(BigDecimal.ZERO)
                        .unallocated(stockroom.getTransferredOut())
                        .status(DistributionStatus.PENDING_ALLOCATION)
                        .build();
                    distributionRepository.save(distribution);
                }
            }
        }

        log.info("Created/updated distribution records for session {}", sessionId);
    }
    
    /**
     * STAGE 3: Save well inventory (allocation to wells)
     */
  
    
    /**
     * Update distribution record when stock is allocated to wells
     */
    private void updateDistributionAllocation(Long sessionId, Long productId, BigDecimal quantity) {
        DistributionRecord distribution = distributionRepository
            .findBySessionSessionIdAndProductProductId(sessionId, productId)
            .orElseThrow(() -> new RuntimeException("Distribution record not found"));
        
        BigDecimal newTotal = distribution.getTotalAllocated().add(quantity);
        distribution.setTotalAllocated(newTotal);
        distributionRepository.save(distribution);
    }
    
    /**
     * FINAL STAGE: Commit session after validations
     */
    @Transactional
    public void commitSession(Long sessionId) {
        InventorySession session = getSessionInProgress(sessionId);
        
        // Perform all validations
        StringBuilder errors = new StringBuilder();
        
        
        // Validation 1: Stockroom transferred = Distribution total
        if (!validateStockroomToDistribution(sessionId, errors)) {
            rollbackSession(sessionId, errors.toString());
            throw new RuntimeException("Validation failed: " + errors.toString());
        }
        
        // Validation 2: Distribution allocated = Wells received
        if (!validateDistributionToWells(sessionId, errors)) {
            rollbackSession(sessionId, errors.toString());
            throw new RuntimeException("Validation failed: " + errors.toString());
        }
        
        // Validation 3: No unallocated stock in distribution
        if (!validateNoUnallocatedStock(sessionId, errors)) {
            rollbackSession(sessionId, errors.toString());
            throw new RuntimeException("Validation failed: " + errors.toString());
        }
     // Before generateSalesRecords(sessionId):
        validatePricesExist(sessionId, session);
        
        // All validations passed - generate sales and commit
        generateSalesRecords(sessionId);
        
        
        session.setStatus(SessionStatus.COMPLETED);
        session.setSessionEndTime(LocalDateTime.now());
        sessionRepository.save(session);
        
        log.info("Session {} committed successfully", sessionId);
    }
    
    private void validatePricesExist(Long sessionId, InventorySession session) {
        List<WellInventory> wells = wellRepository.findBySessionSessionId(sessionId);
        for (WellInventory w : wells) {
            priceRepository.findByBarBarIdAndProductProductId(
                session.getBar().getBarId(), w.getProduct().getProductId())
                .orElseThrow(() -> new RuntimeException(
                    "No price configured for: " + w.getProduct().getProductName()));
        }
    }
    
    /**
     * Validation 1: Stockroom transferred must equal distribution total
     */
    private boolean validateStockroomToDistribution(Long sessionId, StringBuilder errors) {
        List<StockroomInventory> stockrooms = stockroomRepository.findBySessionSessionId(sessionId);
        List<DistributionRecord> distributions = distributionRepository.findBySessionSessionId(sessionId);
        
        for (StockroomInventory stockroom : stockrooms) {
            BigDecimal transferred = stockroom.getTransferredOut();
            
            DistributionRecord distribution = distributions.stream()
                .filter(d -> d.getProduct().getProductId().equals(stockroom.getProduct().getProductId()))
                .findFirst()
                .orElse(null);
            
            if (distribution == null && transferred.compareTo(BigDecimal.ZERO) > 0) {
                errors.append("Product ").append(stockroom.getProduct().getProductName())
                    .append(": No distribution record found for transferred stock. ");
                return false;
            }
            
            if (distribution != null && 
                transferred.compareTo(distribution.getQuantityFromStockroom()) != 0) {
                errors.append("Product ").append(stockroom.getProduct().getProductName())
                    .append(": Stockroom transferred (").append(transferred)
                    .append(") != Distribution quantity (")
                    .append(distribution.getQuantityFromStockroom()).append("). ");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validation 2: Distribution allocated must equal wells received
     */
    private boolean validateDistributionToWells(Long sessionId, StringBuilder errors) {
        List<DistributionRecord> distributions = distributionRepository.findBySessionSessionId(sessionId);
        
        for (DistributionRecord distribution : distributions) {
            BigDecimal totalWellsReceived = wellRepository.sumReceivedBySessionAndProduct(
                sessionId, distribution.getProduct().getProductId());
            
            if (distribution.getTotalAllocated().compareTo(totalWellsReceived) != 0) {
                errors.append("Product ").append(distribution.getProduct().getProductName())
                    .append(": Distribution allocated (").append(distribution.getTotalAllocated())
                    .append(") != Wells received (").append(totalWellsReceived).append("). ");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validation 3: No stock should remain unallocated
     */
    private boolean validateNoUnallocatedStock(Long sessionId, StringBuilder errors) {
        List<DistributionRecord> distributions = distributionRepository.findBySessionSessionId(sessionId);
        
        for (DistributionRecord distribution : distributions) {
            if (distribution.getUnallocated().compareTo(BigDecimal.ZERO) > 0) {
                errors.append("Product ").append(distribution.getProduct().getProductName())
                    .append(": Unallocated stock remaining (")
                    .append(distribution.getUnallocated()).append(" units). ");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generate sales records from consumed quantities
     */
    private void generateSalesRecords(Long sessionId) {
        InventorySession session = sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        List<WellInventory> wellInventories = wellRepository.findBySessionSessionId(sessionId);

        Map<Long, BigDecimal> consumed = wellInventories.stream()
            .collect(Collectors.groupingBy(
                w -> w.getProduct().getProductId(),
                Collectors.reducing(BigDecimal.ZERO, WellInventory::getConsumed, BigDecimal::add)
            ));

        for (Map.Entry<Long, BigDecimal> entry : consumed.entrySet()) {
            Long productId = entry.getKey();
            BigDecimal totalConsumed = entry.getValue();
            if (totalConsumed.compareTo(BigDecimal.ZERO) <= 0) continue;

            Product product = wellInventories.stream()
                .filter(w -> w.getProduct().getProductId().equals(productId))
                .findFirst().get().getProduct();

            BarProductPrice price = priceRepository
                .findByBarBarIdAndProductProductId(session.getBar().getBarId(), productId)
                .orElseThrow(() -> new RuntimeException(
                    "Price not set for product: " + product.getProductName() +
                    " at bar: " + session.getBar().getBarName()));

            SalesRecord sales = SalesRecord.builder()
                .session(session)
                .product(product)
                .quantitySold(totalConsumed)
                .sellingPricePerUnit(price.getSellingPrice())
                .costPricePerUnit(price.getCostPrice() != null ? price.getCostPrice() : BigDecimal.ZERO)
                .build();

            salesRepository.save(sales);
            log.info("Saved sales record: product={}, qty={}", product.getProductName(), totalConsumed);
        }
    }
    
    
 // In BarService or a new WellService
    public List<String> getWellNamesForBar(Long barId) {
        List<BarWell> wells = barWellRepository.findByBarBarIdAndActiveTrue(barId);

        // ✅ If bar has no configured wells, fall back to defaults
        if (wells == null || wells.isEmpty()) {
            return List.of("BAR_1", "BAR_2", "SERVICE_BAR");
        }

        return wells.stream()
                .map(BarWell::getWellName)
                .collect(Collectors.toList());
    }
    
    
    /**
     * Rollback session in case of validation failure
     */
    @Transactional
    public void rollbackSession(Long sessionId, String errorMessage) {
        InventorySession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setStatus(SessionStatus.ROLLED_BACK);
        session.setSessionEndTime(LocalDateTime.now());
        session.setValidationErrors(errorMessage);
        sessionRepository.save(session);
        
        log.error("Session {} rolled back: {}", sessionId, errorMessage);
    }
    
    /**
     * Get session and verify it's in progress
     */
    private InventorySession getSessionInProgress(Long sessionId) {
        // ✅ FIX: use findByIdWithBar instead of findById
        InventorySession session = sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new RuntimeException("Session is not in progress");
        }
        return session;
    }
    
    /**
     * Get session by ID
     */
 
    /**
     * Get all sessions for a bar
     */
    public List<InventorySession> getSessionsByBar(Long barId) {
        return sessionRepository.findByBarBarIdOrderBySessionStartTimeDesc(barId);
    }
    
    /**
     * Get sessions by date range
     */
    public List<InventorySession> getSessionsByDateRange(Long barId, 
                                                         LocalDateTime startDate, 
                                                         LocalDateTime endDate) {
        return sessionRepository.findSessionsByBarAndDateRange(barId, startDate, endDate);
    }
    

    
 // ✅ Returns "productId_wellName" → receivedFromDistribution
    public Map<String, BigDecimal> getDistributionMapForSession(Long sessionId) {
        List<WellInventory> wells = wellRepository.findBySessionSessionId(sessionId);

        return wells.stream()
                .filter(w -> w.getReceivedFromDistribution()
                              .compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(
                        w -> w.getProduct().getProductId() + "_" + w.getWellName(),
                        WellInventory::getReceivedFromDistribution,
                        BigDecimal::add  // sum if duplicate key
                ));
    }

   
    @Transactional
    public void saveWellInventoryFromForm(Long sessionId, Map<String, String> formData) {

        InventorySession session = getSessionInProgress(sessionId);
        List<Product> products = productService.getAllActiveProducts();

        // Get existing well records saved during distribution
        List<WellInventory> existingWells =
                wellRepository.findBySessionSessionId(sessionId);

        // Build lookup map of existing wells: "productId_wellName" → WellInventory
        Map<String, WellInventory> existingMap = existingWells.stream()
                .collect(Collectors.toMap(
                        w -> w.getProduct().getProductId() + "_" + w.getWellName(),
                        w -> w
                ));

        Long barId = session.getBar().getBarId();
        List<String> wellNames = getWellNamesForBar(barId);
        for (String wellName : wellNames) {
            for (Product product : products) {
                String key      = product.getProductId() + "_" + wellName;
                String formKey  = "opening_" + key;
                String closeKey = "closing_" + key;

                BigDecimal opening = parseDecimal(formData.get(formKey));
                BigDecimal closing = parseDecimal(formData.get(closeKey));

                // Skip rows where user submitted nothing at all
                if (opening.compareTo(BigDecimal.ZERO) == 0
                        && closing.compareTo(BigDecimal.ZERO) == 0
                        && !existingMap.containsKey(key)) {
                    continue;
                }

                WellInventory wi = existingMap.get(key);

                if (wi != null) {
                    // ✅ Existing record (received from distribution) — update opening/closing
                    BigDecimal received = wi.getReceivedFromDistribution();
                    BigDecimal consumed = opening.add(received).subtract(closing);

                    wi.setOpeningStock(opening);
                    wi.setClosingStock(closing);
                    wi.setConsumed(consumed.compareTo(BigDecimal.ZERO) >= 0
                            ? consumed : BigDecimal.ZERO);
                    wellRepository.save(wi);

                    log.debug("Updated existing well: product={}, well={}, " +
                            "opening={}, received={}, closing={}, consumed={}",
                            product.getProductName(), wellName,
                            opening, received, closing, wi.getConsumed());

                } else if (opening.compareTo(BigDecimal.ZERO) > 0
                        || closing.compareTo(BigDecimal.ZERO) > 0) {
                    // ✅ No distribution record but has opening stock — create new record
                    BigDecimal consumed = opening.subtract(closing);

                    WellInventory newWi = WellInventory.builder()
                            .session(session)
                            .product(product)
                            .wellName(wellName)
                            .openingStock(opening)
                            .receivedFromDistribution(BigDecimal.ZERO) // no distribution
                            .closingStock(closing)
                            .consumed(consumed.compareTo(BigDecimal.ZERO) >= 0
                                    ? consumed : BigDecimal.ZERO)
                            .build();
                    wellRepository.save(newWi);

                    log.debug("Created zero-received well: product={}, well={}, " +
                            "opening={}, received=0, closing={}, consumed={}",
                            product.getProductName(), wellName,
                            opening, closing, newWi.getConsumed());
                }
            }
        }

        log.info("saveWellInventoryFromForm complete for session {}", sessionId);
    }
    
    @Transactional
    public void saveWellInventory(Long sessionId, List<WellInventory> wellInventories) {
        InventorySession session = getSessionInProgress(sessionId);

        for (WellInventory wellInventory : wellInventories) {
            wellInventory.setSession(session);
            wellRepository.save(wellInventory);

            // Only update allocation if received > 0
            if (wellInventory.getReceivedFromDistribution().compareTo(BigDecimal.ZERO) > 0) {
                updateDistributionAllocation(sessionId,
                        wellInventory.getProduct().getProductId(),
                        wellInventory.getReceivedFromDistribution());
            }
        }

        log.info("Saved {} well inventory records for session {}", wellInventories.size(), sessionId);
    }

    private BigDecimal parseDecimal(String val) {
        return (val != null && !val.isEmpty()) ? new BigDecimal(val) : BigDecimal.ZERO;
    }
    
    @Transactional
    public void saveDistributionAllocations(Long sessionId, Map<String, String> formData) {
        InventorySession session = getSessionInProgress(sessionId);
        List<Product> products = productService.getAllActiveProducts();

        // ✅ Clear existing well records
        wellRepository.deleteBySessionSessionId(sessionId);

        // ✅ Reset all distribution totalAllocated to zero before recalculating
        List<DistributionRecord> distributions =
                distributionRepository.findBySessionSessionId(sessionId);
        for (DistributionRecord dr : distributions) {
            dr.setTotalAllocated(BigDecimal.ZERO);
            dr.setUnallocated(dr.getQuantityFromStockroom()); // reset unallocated too
            dr.setStatus(DistributionStatus.PENDING_ALLOCATION);
            distributionRepository.save(dr);
        }

        List<WellInventory> wellInventories = new ArrayList<>();

        for (Product product : products) {
        	for (String wellName : getWellNamesForBar(session.getBar().getBarId())) {

                String key = "alloc_" + product.getProductId() + "_" + wellName;
                BigDecimal allocated = parseDecimal(formData.get(key));

                if (allocated.compareTo(BigDecimal.ZERO) > 0) {

                    // ✅ Check distribution record exists for this product
                    Optional<DistributionRecord> distOpt = distributionRepository
                            .findBySessionSessionIdAndProductProductId(
                                    sessionId, product.getProductId());

                    if (distOpt.isEmpty()) continue; // skip products not in distribution

                    WellInventory wi = WellInventory.builder()
                            .session(session)
                            .product(product)
                            .wellName(wellName)
                            .openingStock(BigDecimal.ZERO)
                            .receivedFromDistribution(allocated)
                            .closingStock(BigDecimal.ZERO)
                            .consumed(BigDecimal.ZERO) // updated later on wells page
                            .build();

                    wellInventories.add(wi);
                }
            }
        }

        // ✅ Save wells and update distribution allocation totals
        for (WellInventory wi : wellInventories) {
            wi.setSession(session);
            wellRepository.save(wi);
            updateDistributionAllocation(sessionId,
                    wi.getProduct().getProductId(),
                    wi.getReceivedFromDistribution());
        }

        // ✅ Update unallocated field and status on each distribution record
        for (DistributionRecord dr : distributionRepository.findBySessionSessionId(sessionId)) {
            BigDecimal totalReceived = wellRepository.sumReceivedBySessionAndProduct(
                    sessionId, dr.getProduct().getProductId());
            dr.setTotalAllocated(totalReceived);
            dr.setUnallocated(dr.getQuantityFromStockroom().subtract(totalReceived));
            dr.setStatus(dr.getUnallocated().compareTo(BigDecimal.ZERO) == 0
                    ? DistributionStatus.COMPLETED
                    : DistributionStatus.PENDING_ALLOCATION);
            distributionRepository.save(dr);
        }

        log.info("Saved distribution allocations for session {}, {} well records",
                sessionId, wellInventories.size());
    }
    
    
    
    /**
     * Returns productId → previousClosingStock for stockroom opening pre-fill
     */
    public Map<Long, BigDecimal> getPreviousClosingForStockroom(Long barId) {
        List<InventorySession> completed =
                sessionRepository.findCompletedSessionsByBar(barId);

        if (completed.isEmpty()) {
            log.info("No previous completed session found for bar {}", barId);
            return Map.of(); // first ever session - all zeros
        }

        Long lastSessionId = completed.get(0).getSessionId();
        log.info("Pre-filling stockroom opening from session {} for bar {}",
                lastSessionId, barId);

        List<StockroomInventory> stockrooms =
                stockroomRepository.findBySessionSessionId(lastSessionId);

        return stockrooms.stream()
                .collect(Collectors.toMap(
                        s -> s.getProduct().getProductId(),
                        StockroomInventory::getClosingStock,
                        (a, b) -> a // keep first if duplicate
                ));
    }

    /**
     * Returns "productId_wellName" → previousClosingStock for wells opening pre-fill
     */
    public Map<String, BigDecimal> getPreviousClosingForWells(Long barId) {
        List<InventorySession> completed =
                sessionRepository.findCompletedSessionsByBar(barId);

        if (completed.isEmpty()) {
            log.info("No previous completed session found for bar {}", barId);
            return Map.of(); // first ever session - all zeros
        }

        Long lastSessionId = completed.get(0).getSessionId();
        log.info("Pre-filling wells opening from session {} for bar {}",
                lastSessionId, barId);

        List<WellInventory> wells =
                wellRepository.findBySessionSessionId(lastSessionId);

        return wells.stream()
                .collect(Collectors.toMap(
                        w -> w.getProduct().getProductId() + "_" + w.getWellName(),
                        WellInventory::getClosingStock,
                        (a, b) -> a
                ));
    }
    
    /**
     * ADMIN: Create a SETUP session for initial stock seeding
     */
    @Transactional
    public InventorySession createSetupSession(Long barId) {
        Bar bar = barRepository.findById(barId)
            .orElseThrow(() -> new RuntimeException("Bar not found"));

        Optional<InventorySession> existing = sessionRepository
            .findFirstByBarBarIdAndStatusOrderBySessionStartTimeDesc(barId, SessionStatus.SETUP);
        if (existing.isPresent()) {
            // ✅ FIXED: was existing.get() — bar would be null lazy proxy
            return sessionRepository.findByIdWithBar(existing.get().getSessionId())
                .orElseThrow(() -> new RuntimeException("Session not found"));
        }

        InventorySession session = InventorySession.builder()
            .bar(bar)
            .sessionStartTime(LocalDateTime.now())
            .status(SessionStatus.SETUP)
            .shiftType("SETUP")
            .notes("Admin initial stock setup")
            .stockroomInventories(new ArrayList<>())
            .distributionRecords(new ArrayList<>())
            .wellInventories(new ArrayList<>())
            .salesRecords(new ArrayList<>())
            .build();

        InventorySession saved = sessionRepository.save(session);
        return sessionRepository.findByIdWithBar(saved.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found after save"));
    }
    /**
     * ADMIN: Save initial stockroom opening stock
     * Stores admin's value as BOTH openingStock and closingStock
     * so transferredOut = 0, and getPreviousClosingForStockroom() picks it up correctly
     */
    @Transactional
    public void saveSetupStockroom(Long sessionId, Map<String, String> formData) {
        InventorySession session = sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != SessionStatus.SETUP) {
            throw new RuntimeException("Not a setup session");
        }

        stockroomRepository.deleteBySessionSessionId(sessionId);
        List<Product> products = productService.getAllActiveProducts();

        for (Product product : products) {
            String key = "opening_" + product.getProductId();
            BigDecimal openingQty = parseDecimal(formData.get(key));

            if (openingQty.compareTo(BigDecimal.ZERO) > 0) {
                StockroomInventory si = StockroomInventory.builder()
                    .session(session)
                    .product(product)
                    .openingStock(openingQty)       // same value in both fields
                    .receivedStock(BigDecimal.ZERO)
                    .closingStock(openingQty)       // ← this is what getPreviousClosing reads
                    .transferredOut(BigDecimal.ZERO)
                    .remarks("Admin initial setup")
                    .build();
                stockroomRepository.save(si);
            }
        }
        log.info("Saved setup stockroom for session {}", sessionId);
    }

    /**
     * ADMIN: Save initial wells opening stock
     */
    @Transactional
    public void saveSetupWells(Long sessionId, Map<String, String> formData) {
        InventorySession session = sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != SessionStatus.SETUP) {
            throw new RuntimeException("Not a setup session");
        }

        wellRepository.deleteBySessionSessionId(sessionId);
        List<Product> products = productService.getAllActiveProducts();

        // ✅ Use bar's configured wells
        List<String> wellNames = getWellNamesForBar(session.getBar().getBarId());

        for (String wellName : wellNames) {
            for (Product product : products) {
                String key = "opening_" + product.getProductId() + "_" + wellName;
                BigDecimal openingQty = parseDecimal(formData.get(key));

                if (openingQty.compareTo(BigDecimal.ZERO) > 0) {
                    WellInventory wi = WellInventory.builder()
                        .session(session)
                        .product(product)
                        .wellName(wellName)
                        .openingStock(openingQty)
                        .receivedFromDistribution(BigDecimal.ZERO)
                        .closingStock(openingQty)
                        .consumed(BigDecimal.ZERO)
                        .remarks("Admin initial setup")
                        .build();
                    wellRepository.save(wi);
                }
            }
        }
    }

    /**
     * ADMIN: Finalize setup — mark as COMPLETED so pre-fill logic picks it up
     */
    @Transactional
    public void finalizeSetupSession(Long sessionId) {
        InventorySession session = sessionRepository.findByIdWithBar(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        if (session.getStatus() != SessionStatus.SETUP) {
            throw new RuntimeException("Not a setup session");
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setSessionEndTime(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Setup session {} finalized for bar {}", sessionId, session.getBar().getBarId());
    }
    
    
    public Optional<InventorySession> getSetupSession(Long barId) {
        Optional<InventorySession> found = sessionRepository
            .findFirstByBarBarIdAndStatusOrderBySessionStartTimeDesc(barId, SessionStatus.SETUP);
        if (found.isPresent()) {
            return sessionRepository.findByIdWithBar(found.get().getSessionId());
        }
        return Optional.empty();
    }

    public Map<Long, BigDecimal> getSetupStockroomData(Long sessionId) {
        return stockroomRepository.findBySessionSessionId(sessionId).stream()
            .collect(Collectors.toMap(
                s -> s.getProduct().getProductId(),
                StockroomInventory::getClosingStock
            ));
    }

    public Map<String, BigDecimal> getSetupWellsData(Long sessionId) {
        return wellRepository.findBySessionSessionId(sessionId).stream()
            .collect(Collectors.toMap(
                w -> w.getProduct().getProductId() + "_" + w.getWellName(),
                WellInventory::getClosingStock
            ));
    }
 
}
