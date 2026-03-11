package com.barinventory.billing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.barinventory.admin.entity.User;
import com.barinventory.admin.repository.UserRepository;
import com.barinventory.billing.dtos.BillItemRequest;
import com.barinventory.billing.dtos.BillResponse;
import com.barinventory.billing.dtos.BrandBillingDTO;
import com.barinventory.billing.dtos.BrandDTO;
import com.barinventory.billing.dtos.BrandSizeDTO;
import com.barinventory.billing.dtos.CreateBillRequest;
import com.barinventory.billing.entity.Bill;
import com.barinventory.billing.entity.BillItem;
import com.barinventory.billing.repository.BillRepository;
import com.barinventory.brands.entity.Brand;
import com.barinventory.brands.entity.BrandSize;
import com.barinventory.brands.repository.BrandRepository;
import com.barinventory.brands.repository.BrandSizeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BillingServiceImpl implements BillingService {

    private final BrandRepository brandRepository;
    private final BrandSizeRepository brandSizeRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;

    // ===================== BRAND MANAGEMENT ====================

    @Override
    @Transactional(readOnly = true)
    public BrandDTO getBrandById(Long id) {
        Brand brand = brandRepository.findByIdWithSizes(id)
                .orElseThrow(() -> new RuntimeException("Brand not found: " + id));

        return mapToBrandDTO(brand);
    }

    @Override
    public BrandDTO createBrand(BrandDTO dto) {
        Brand brand = Brand.builder()
                .brandName(dto.getName())
                .parentCompany(dto.getParentCompany())
                .category(dto.getCategory())
                .exciseCode(dto.getExciseCode())
                .active(true)
                .sizes(new ArrayList<>())
                .build();

        if (dto.getSizes() != null) {
            List<BrandSize> sizes = dto.getSizes().stream()
                    .map(s -> mapToSizeEntity(s, brand))
                    .collect(Collectors.toList());
            brand.getSizes().addAll(sizes);
        }

        return mapToBrandDTO(brandRepository.save(brand));
    }

    @Override
    public BrandDTO updateBrand(Long id, BrandDTO dto) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found: " + id));
        brand.setBrandName(dto.getName());
        brand.setParentCompany(dto.getParentCompany());
        brand.setCategory(dto.getCategory());
        brand.setExciseCode(dto.getExciseCode());
        return mapToBrandDTO(brandRepository.save(brand));
    }

    @Override
    public void deactivateBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand not found: " + id));
        brand.setActive(false);
        brandRepository.save(brand);
    }

   /* @Override
    @Transactional(readOnly = true)
    public List<BrandDTO> getAllActiveBrands() {
        return brandRepository.findByActiveTrue().stream()
                .map(this::mapToBrandDTO)
                .collect(Collectors.toList());
    }*/
    @Override
    @Transactional(readOnly = true)
    public List<BrandBillingDTO> getAllActiveBrands2() {
        List<Brand> brands = brandRepository.findAllActiveWithSizes();

        return brands.stream()
                .map(brand -> new BrandBillingDTO(
                        brand.getId(),
                        brand.getBrandName(),
                        brand.getSizes().stream()
                                .filter(BrandSize::isActive) // only active sizes
                                .map(size -> new BrandBillingDTO.BrandSizeDTO(
                                        size.getId(),
                                        size.getSizeLabel(),
                                        size.getMrp(),
                                        size.isActive()
                                ))
                                .toList()
                ))
                .toList();
    }
    @Override
    public List<BrandSize> getAllSizes() {
        return brandSizeRepository.findAll();
    }
    
    
    @Override
    @Transactional(readOnly = true)
    public List<BrandDTO> getBrandsByCategory(Brand.Category category) {
        return brandRepository.findByCategoryAndActiveTrue(category).stream()
                .map(this::mapToBrandDTO)
                .collect(Collectors.toList());
    }

    // ===================== BILLING =====================

    @Override
    public BillResponse createBill(CreateBillRequest request, String username) {
    	User user = userRepository.findByEmail(username)
    	        .orElseThrow(() -> new RuntimeException("User not found with email: " + username));
        Bill bill = Bill.builder()
                .user(user)
                .finalized(false)
                .items(new ArrayList<>())
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (BillItemRequest itemReq : request.getItems()) {
            BrandSize size = brandSizeRepository.findById(itemReq.getBrandSizeId())
                    .orElseThrow(() -> new RuntimeException("BrandSize not found: " + itemReq.getBrandSizeId()));

            if (!size.isActive() || !size.getBrand().isActive()) {
                throw new RuntimeException("Brand/Size is inactive: " + itemReq.getBrandSizeId());
            }

            BigDecimal lineTotal = size.getMrp().multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            BillItem item = BillItem.builder()
                    .bill(bill)
                    .brandId(size.getBrand().getId())
                    .brandName(size.getBrand().getBrandName())   // SNAPSHOT
                    .sizeLabel(size.getSizeLabel())          // SNAPSHOT
                    .unitPrice(size.getMrp())              // SNAPSHOT
                    .quantity(itemReq.getQuantity())
                    .lineTotal(lineTotal)
                    .build();

            bill.getItems().add(item);
            grandTotal = grandTotal.add(lineTotal);
        }

        bill.setGrandTotal(grandTotal);
        bill.setFinalized(true);

        Bill saved = billRepository.save(bill);
        return mapToBillResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillResponse> getBillsForUser(String username) {
    	User user = userRepository.findByEmail(username)
    	        .orElseThrow(() -> new RuntimeException("User not found with email: " + username));
        return billRepository.findByUserIdWithItems(user.getId()).stream()
                .map(this::mapToBillResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BillResponse getBillById(Long billId, String username) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (!bill.getUser().getEmail().equals(username))  {
            throw new RuntimeException("Unauthorized");
        }
        return mapToBillResponse(bill);
    }
    @Override
    public Bill getBill(Long id) {

        return billRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Bill not found with id: " + id));

    }
    
    @Override
    public Bill findById(Long id) {
        return billRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
    }

    // ===================== MAPPERS =====================
    
    @Override
    public void addSizeToBrand(Long brandId, BrandSizeDTO dto) {

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found: " + brandId));

        if (!brand.isActive()) {
            throw new RuntimeException("Cannot add size to inactive brand.");
        }

        // Create size entity
        BrandSize size = BrandSize.builder()
                .brand(brand)  // VERY IMPORTANT (FK mapping)
                .sizeLabel(dto.getSizeLabel())
                .mrp(dto.getPrice())
                .packaging(dto.getPackaging())
                .abvPercent(dto.getAbvPercent())
                .displayOrder(dto.getDisplayOrder())
                .active(true)
                .build();

        // Add to brand collection (important for cascade consistency)
        brand.getSizes().add(size);

        // Save using brand (cascade will persist size)
        brandRepository.save(brand);
    }

    @Override
    public void deactivateSize(Long sizeId) {

        BrandSize size = brandSizeRepository.findById(sizeId)
                .orElseThrow(() -> new RuntimeException("Size not found: " + sizeId));

        if (!size.isActive()) {
            throw new RuntimeException("Size already inactive.");
        }

        size.setActive(false);
    }
    private BrandDTO mapToBrandDTO(Brand brand) {
        return BrandDTO.builder()
                .id(brand.getId())
                .name(brand.getBrandName())
                .parentCompany(brand.getParentCompany())
                .category(brand.getCategory())
                .exciseCode(brand.getExciseCode())
                .active(brand.isActive())
                .sizes(brand.getSizes() == null ? List.of() :
                        brand.getSizes().stream().map(this::mapToSizeDTO).collect(Collectors.toList()))
                .build();
    }

    private BrandSizeDTO mapToSizeDTO(BrandSize size) {
        return BrandSizeDTO.builder()
                .id(size.getId())
                .sizeLabel(size.getSizeLabel())
                .price(size.getMrp())
                .packaging(size.getPackaging())
                .abvPercent(size.getAbvPercent())
                .displayOrder(size.getDisplayOrder())
                .active(size.isActive())
                .build();
    }

    private BrandSize mapToSizeEntity(BrandSizeDTO dto, Brand brand) {
        return BrandSize.builder()
                .brand(brand)
                .sizeLabel(dto.getSizeLabel())
                .mrp(dto.getPrice())
                .packaging(dto.getPackaging())
                .abvPercent(dto.getAbvPercent())
                .displayOrder(dto.getDisplayOrder())
                .active(true)
                .build();
    }

    private BillResponse mapToBillResponse(Bill bill) {
        List<BillResponse.BillItemResponse> itemResponses = bill.getItems().stream()
                .map(i -> BillResponse.BillItemResponse.builder()
                        .brandId(i.getBrandId())
                        .brandName(i.getBrandName())
                        .sizeLabel(i.getSizeLabel())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        return BillResponse.builder()
                .billId(bill.getId())
                .createdBy(bill.getUser().getUsername())
                .createdAt(bill.getCreatedAt())
                .grandTotal(bill.getGrandTotal())
                .finalized(bill.isFinalized())
                .items(itemResponses)
                .build();
    }
}