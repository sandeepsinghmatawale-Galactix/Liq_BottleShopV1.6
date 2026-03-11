package com.barinventory.admin.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.barinventory.admin.entity.Bar;
import com.barinventory.admin.entity.BarProductPrice;
import com.barinventory.admin.entity.InventorySession;
import com.barinventory.admin.entity.Product;
import com.barinventory.admin.entity.Role;
import com.barinventory.admin.entity.StockroomInventory;
import com.barinventory.admin.entity.User;
import com.barinventory.admin.repository.BarRepository;
import com.barinventory.admin.repository.BarWellRepository;
import com.barinventory.admin.repository.UserRepository;
import com.barinventory.admin.service.BarService;
import com.barinventory.admin.service.InventorySessionService;
import com.barinventory.admin.service.PricingService;
import com.barinventory.admin.service.ProductService;
import com.barinventory.admin.service.ReportService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final BarService barService;
    private final ProductService productService;
    private final PricingService pricingService;
    private final InventorySessionService sessionService;
    private final ReportService reportService;
    private final UserRepository userRepository;
    private final BarRepository barRepository;
    private final BarWellRepository barWellRepository;

    // ================= LOGIN =================

    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    // ================= ROOT REDIRECT =================

    @GetMapping("/")
    public String redirectToDashboard(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    // ================= DASHBOARD =================

    @GetMapping("/dashboard")
    public String showDashboard(@AuthenticationPrincipal User currentUser, Model model) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("username", currentUser.getName());
        model.addAttribute("role", currentUser.getRole());

        if (currentUser.getRole() == Role.ADMIN) {
            List<Bar> bars = barRepository.findAll();
            model.addAttribute("bars", bars);
            model.addAttribute("totalBars", bars.size());
            model.addAttribute("totalUsers", userRepository.count());
            return "dashboard";
        }

        // BAR_OWNER / BAR_STAFF
        Long barId = currentUser.getBarId();
        if (barId == null) {
            return "redirect:/login?error=bar_not_assigned";
        }

        Bar bar = barRepository.findById(barId).orElse(null);
        if (bar == null) {
            return "redirect:/login?error=invalid_bar";
        }

        model.addAttribute("bar", bar);
        model.addAttribute("barId", barId);

        if (currentUser.getRole() == Role.BAR_OWNER) {
            long staffCount = userRepository.countByBar_BarIdAndRole(barId, Role.BAR_STAFF);
            model.addAttribute("staffCount", staffCount);
        }

        return "dashboard";
    }

    // ═══════════════════════════════════════════════════
    //  BAR ONBOARDING WIZARD
    // ═══════════════════════════════════════════════════

    @GetMapping("/admin/bars/register")
    public String showRegisterBar(Model model) {
        model.addAttribute("barTypes", List.of("STANDALONE", "HOTEL_BAR", "RESTAURANT_BAR", "CLUB", "OTHER"));
        model.addAttribute("shiftConfigs", List.of("SINGLE", "DOUBLE"));
        return "admin/bar-register";
    }

    @PostMapping("/admin/bars/register")
    public String saveRegisterBar(@RequestParam Map<String, String> form) {
        Bar bar = Bar.builder()
                .barName(form.get("barName"))
                .barType(form.get("barType"))
                .ownerName(form.get("ownerName"))
                .contactNumber(form.get("contactNumber"))
                .email(form.get("email"))
                .addressLine(form.get("addressLine"))
                .city(form.get("city"))
                .state(form.get("state"))
                .pinCode(form.get("pinCode"))
                .licenseNumber(form.get("licenseNumber"))
                .licenseType(form.get("licenseType"))
                .licenseExpiryDate(form.get("licenseExpiryDate") != null && !form.get("licenseExpiryDate").isEmpty()
                        ? LocalDate.parse(form.get("licenseExpiryDate")) : null)
                .gstin(form.get("gstin"))
                .shiftConfig(form.get("shiftConfig"))
                .openingDate(form.get("openingDate") != null && !form.get("openingDate").isEmpty()
                        ? LocalDate.parse(form.get("openingDate")) : null)
                .active(false)
                .setupComplete(false)
                .onboardingStep("WELL_CONFIG")
                .build();

        Bar saved = barService.createBar(bar);
        return "redirect:/admin/bars/" + saved.getBarId() + "/wells-config";
    }

    @GetMapping("/admin/bars/{barId}/wells-config")
    public String showWellsConfig(@PathVariable Long barId, Model model) {
        model.addAttribute("bar", barService.getBarById(barId));
        model.addAttribute("existingWells", barWellRepository.findByBarBarIdAndActiveTrue(barId));
        return "admin/bar-wells-config";
    }

    @PostMapping("/admin/bars/{barId}/wells-config")
    public String saveWellsConfig(@PathVariable Long barId, @RequestParam Map<String, String> form) {
        barService.saveWellsConfig(barId, form);
        barService.updateOnboardingStep(barId, "PRICING");
        return "redirect:/admin/bars/" + barId + "/pricing";
    }

    @GetMapping("/admin/bars/{barId}/pricing")
    public String showPricing(@PathVariable Long barId, Model model) {
        model.addAttribute("bar", barService.getBarById(barId));
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("existingPrices", pricingService.getPricesForBar(barId));
        return "admin/bar-pricing";
    }

    @PostMapping("/admin/bars/{barId}/pricing")
    public String savePricing(@PathVariable Long barId, @RequestParam Map<String, String> form) {
        pricingService.savePricesFromForm(barId, form);
        barService.updateOnboardingStep(barId, "OPENING_STOCK");
        return "redirect:/admin/bars/" + barId + "/setup";
    }

    @GetMapping("/admin/bars/{barId}/setup")
    public String showSetupLanding(@PathVariable Long barId, Model model) {
        model.addAttribute("bar", barService.getBarById(barId));
        Optional<InventorySession> existing = sessionService.getSetupSession(barId);
        model.addAttribute("setupSession", existing.orElse(null));
        return "admin/setup-landing";
    }

    @PostMapping("/admin/bars/{barId}/activate")
    public String activateBar(@PathVariable Long barId) {
        barService.activateBar(barId);
        return "redirect:/dashboard?activated=" + barId;
    }

    // ================= ADMIN BAR SETUP (OPENING STOCK) =================

    @PostMapping("/admin/bars/{barId}/setup/start")
    public String startSetup(@PathVariable Long barId) {
        InventorySession session = sessionService.createSetupSession(barId);
        return "redirect:/admin/setup/" + session.getSessionId() + "/stockroom";
    }

    @GetMapping("/admin/setup/{sessionId}/stockroom")
    public String showSetupStockroom(@PathVariable Long sessionId, Model model) {
        InventorySession session = sessionService.getSession(sessionId);
        model.addAttribute("session", session);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("existingStock", sessionService.getSetupStockroomData(sessionId));
        return "admin/setup-stockroom";
    }

    @PostMapping("/admin/setup/{sessionId}/stockroom")
    public String saveSetupStockroom(@PathVariable Long sessionId,
            @RequestParam Map<String, String> formData, Model model) {
        try {
            sessionService.saveSetupStockroom(sessionId, formData);
            return "redirect:/admin/setup/" + sessionId + "/wells";
        } catch (Exception e) {
            InventorySession session = sessionService.getSession(sessionId);
            model.addAttribute("session", session);
            model.addAttribute("products", productService.getAllActiveProducts());
            model.addAttribute("existingStock", sessionService.getSetupStockroomData(sessionId));
            model.addAttribute("error", e.getMessage());
            return "admin/setup-stockroom";
        }
    }

    @GetMapping("/admin/setup/{sessionId}/wells")
    public String showSetupWells(@PathVariable Long sessionId, Model model) {
        InventorySession session = sessionService.getSession(sessionId);
        model.addAttribute("session", session);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("wellNames", sessionService.getWellNamesForBar(session.getBar().getBarId()));
        model.addAttribute("existingStock", sessionService.getSetupWellsData(sessionId));
        return "admin/setup-wells";
    }

    @PostMapping("/admin/setup/{sessionId}/wells")
    public String saveSetupWells(@PathVariable Long sessionId, @RequestParam Map<String, String> formData) {
        sessionService.saveSetupWells(sessionId, formData);
        return "redirect:/admin/setup/" + sessionId + "/confirm";
    }

    @GetMapping("/admin/setup/{sessionId}/confirm")
    public String showSetupConfirm(@PathVariable Long sessionId, Model model) {
        InventorySession session = sessionService.getSession(sessionId);
        model.addAttribute("session", session);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("stockroomData", sessionService.getSetupStockroomData(sessionId));
        model.addAttribute("wellsData", sessionService.getSetupWellsData(sessionId));
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("wellNames", sessionService.getWellNamesForBar(session.getBar().getBarId()));
        return "admin/setup-confirm";
    }

    @PostMapping("/admin/setup/{sessionId}/finalize")
    public String finalizeSetup(@PathVariable Long sessionId) {
        sessionService.finalizeSetupSession(sessionId);
        return "redirect:/dashboard?setupComplete=true";
    }

    // ═══════════════════════════════════════════════════
    //  SESSIONS
    // ═══════════════════════════════════════════════════

    @GetMapping("/sessions/{barId}")
    public String listSessions(@PathVariable Long barId, Model model) {
        model.addAttribute("sessions", sessionService.getSessionsByBar(barId));
        model.addAttribute("bar", barService.getBarById(barId));
        return "list";
    }

    @GetMapping("/sessions/{barId}/new")
    public String newSessionForm(@PathVariable Long barId, Model model) {
        model.addAttribute("bar", barService.getBarById(barId));
        return "new";
    }

    @PostMapping("/sessions/{barId}/new")
    public String createSession(@PathVariable Long barId,
                                @RequestParam String shiftType,
                                @RequestParam(required = false) String notes) {
        InventorySession inv = sessionService.initializeSession(barId, shiftType, notes);
        return "redirect:/stockroom/" + inv.getSessionId();
    }

    // ═══════════════════════════════════════════════════
    //  STOCKROOM
    // ═══════════════════════════════════════════════════

    @GetMapping("/stockroom/{sessionId}")
    public String viewStockroom(@PathVariable Long sessionId, Model model) {
        InventorySession inv = sessionService.getSession(sessionId);
        List<Product> products = productService.getAllActiveProducts();

        if (products.isEmpty()) {
            model.addAttribute("error", "No active products found. Please add products first.");
        }

        // ✅ ADD THIS — fetch previous closing stock for pre-fill
        Map<Long, BigDecimal> previousClosing = 
            sessionService.getPreviousClosingForStockroom(inv.getBar().getBarId());

        model.addAttribute("inv", inv);
        model.addAttribute("products", products);
        model.addAttribute("previousClosing", previousClosing); // ✅ pass to template
        return "stockroom";
    }
    
    @PostMapping("/stockroom/{sessionId}")
    public String saveStockroom(@PathVariable Long sessionId,
            @RequestParam Map<String, String> formData, Model model) {
        try {
            InventorySession inv = sessionService.getSession(sessionId);
            List<Product> products = productService.getAllActiveProducts();
            List<StockroomInventory> inventories = new ArrayList<>();

            for (Product product : products) {
                String opening  = formData.get("opening_"  + product.getProductId());
                String received = formData.get("received_" + product.getProductId());
                String closing  = formData.get("closing_"  + product.getProductId());
                String remarks  = formData.get("remarks_"  + product.getProductId());

                if (opening != null || received != null || closing != null) {
                    BigDecimal o = parseDecimal(opening);
                    BigDecimal r = parseDecimal(received);
                    BigDecimal c = parseDecimal(closing);
                    BigDecimal transferred = o.add(r).subtract(c); // ✅ calculate transferred

                    StockroomInventory inventory = StockroomInventory.builder()
                            .session(inv)
                            .product(product)
                            .openingStock(o)
                            .receivedStock(r)
                            .closingStock(c)
                            .transferredOut(transferred.compareTo(BigDecimal.ZERO) >= 0
                                    ? transferred : BigDecimal.ZERO) // ✅ never negative
                            .remarks(remarks)
                            .build();
                    inventories.add(inventory);
                }
            }

            sessionService.saveStockroomInventory(sessionId, inventories);
            sessionService.createDistributionRecords(sessionId);
            return "redirect:/sessions/distribution/" + sessionId;

        } catch (Exception e) {
            InventorySession inv = sessionService.getSession(sessionId);
            // ✅ re-fetch previousClosing on error too
            Map<Long, BigDecimal> previousClosing =
                sessionService.getPreviousClosingForStockroom(inv.getBar().getBarId());
            model.addAttribute("inv", inv);
            model.addAttribute("products", productService.getAllActiveProducts());
            model.addAttribute("previousClosing", previousClosing);
            model.addAttribute("error", e.getMessage());
            return "stockroom";
        }
    }
    // ═══════════════════════════════════════════════════
    //  DISTRIBUTION
    // ═══════════════════════════════════════════════════

    @GetMapping("/sessions/distribution/{sessionId}")
    public String distributionPage(@PathVariable Long sessionId, Model model) {
        InventorySession inv = sessionService.getSession(sessionId);

        if (inv == null)
            throw new RuntimeException("Session not found: " + sessionId);
        if (inv.getBar() == null)
            throw new RuntimeException("Bar not mapped to session: " + sessionId);

        model.addAttribute("session", inv);
        model.addAttribute("inv", inv);
        model.addAttribute("bar", inv.getBar());
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("wellNames", sessionService.getWellNamesForBar(inv.getBar().getBarId()));
        model.addAttribute("distributions",
                inv.getDistributionRecords() != null ? inv.getDistributionRecords() : List.of());
        return "distribution";
    }

    @PostMapping("/sessions/distribution/{sessionId}/save")
    public String saveDistributionAllocations(@PathVariable Long sessionId,
            @RequestParam Map<String, String> formData, Model model) {
        try {
            sessionService.saveDistributionAllocations(sessionId, formData);
            return "redirect:/sessions/wells/" + sessionId;

        } catch (Exception e) {
            InventorySession inv = sessionService.getSession(sessionId);
            model.addAttribute("inv", inv);
            model.addAttribute("bar", inv.getBar());
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("wellNames", sessionService.getWellNamesForBar(inv.getBar().getBarId()));
            model.addAttribute("distributions",
                    inv.getDistributionRecords() != null ? inv.getDistributionRecords() : List.of());
            model.addAttribute("error", e.getMessage());
            return "distribution";
        }
    }

    // ═══════════════════════════════════════════════════
    //  WELLS
    // ═══════════════════════════════════════════════════

    @GetMapping("/sessions/wells/{sessionId}")
    public String wellsPage(@PathVariable Long sessionId, Model model) {
        InventorySession inv = sessionService.getSession(sessionId);
        Bar bar = inv.getBar();

        Map<Long, BarProductPrice> prices = pricingService.getPriceMapForBar(bar.getBarId());
        Map<String, BigDecimal> distributionMap = sessionService.getDistributionMapForSession(sessionId); // ✅ String key
        Map<String, BigDecimal> previousClosing = sessionService.getPreviousClosingForWells(bar.getBarId());

        model.addAttribute("session", inv);
        model.addAttribute("inv", inv);
        model.addAttribute("bar", bar);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("barId", bar.getBarId());
        model.addAttribute("prices", prices);
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("distributionMap", distributionMap);
        model.addAttribute("previousClosing", previousClosing);
        model.addAttribute("wellNames", sessionService.getWellNamesForBar(bar.getBarId()));
        return "wells";
    }

    @PostMapping("/sessions/wells/{sessionId}/save")
    @ResponseBody
    public ResponseEntity<?> saveWellInventory(@PathVariable Long sessionId,
            @RequestParam Map<String, String> formData) {
        try {
            sessionService.saveWellInventoryFromForm(sessionId, formData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ================= PRODUCTS =================

    @GetMapping("/products")
    public String listProducts(Model model) {
        model.addAttribute("products", productService.getAllActiveProducts());
        return "list";
    }

    @GetMapping("/products/new")
    public String newProductForm() {
        return "productNew";
    }

    @PostMapping("/products/new")
    public String createProduct(@RequestParam String productName, @RequestParam String category,
            @RequestParam(required = false) String brand, @RequestParam(required = false) String volumeML,
            @RequestParam String unit) {

        Product product = Product.builder()
                .productName(productName)
                .category(category)
                .brand(brand)
                .volumeML(volumeML != null && !volumeML.isEmpty() ? new BigDecimal(volumeML) : null)
                .unit(unit)
                .active(true)
                .build();

        productService.createProduct(product);
        return "redirect:/products";
    }

    // ================= REPORTS =================

    @GetMapping("/reports/{barId}/daily")
    public String dailyReport(@PathVariable Long barId,
            @RequestParam(required = false) String date, Model model) {
        LocalDateTime reportDate = date != null ? LocalDateTime.parse(date) : LocalDateTime.now();
        model.addAttribute("bar", barService.getBarById(barId));
        model.addAttribute("report", reportService.getDailySalesReport(barId, reportDate));
        return "reports/daily";
    }

    // ================= HELPER =================

    private BigDecimal parseDecimal(String value) {
        return (value != null && !value.isEmpty()) ? new BigDecimal(value) : BigDecimal.ZERO;
    }
}