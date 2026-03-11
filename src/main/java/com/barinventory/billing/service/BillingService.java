package com.barinventory.billing.service;

import java.util.List;

import com.barinventory.billing.dtos.BillResponse;
import com.barinventory.billing.dtos.BrandBillingDTO;
import com.barinventory.billing.dtos.BrandDTO;
import com.barinventory.billing.dtos.BrandSizeDTO;
import com.barinventory.billing.dtos.CreateBillRequest;
import com.barinventory.billing.entity.Bill;
import com.barinventory.brands.entity.Brand;
import com.barinventory.brands.entity.BrandSize;

public interface BillingService {

	// Brand management (admin)
	BrandDTO createBrand(BrandDTO dto);

	BrandDTO updateBrand(Long id, BrandDTO dto);

	void deactivateBrand(Long id);

	//List<BrandDTO> getAllActiveBrands();
   List<BrandBillingDTO> getAllActiveBrands2();

	List<BrandDTO> getBrandsByCategory(Brand.Category category);

	// Billing
	BillResponse createBill(CreateBillRequest request, String username);

	List<BillResponse> getBillsForUser(String username);

	BillResponse getBillById(Long billId, String username);
	
    public BrandDTO getBrandById(Long id);
    
    public void addSizeToBrand(Long brandId, BrandSizeDTO dto);
    
    public void deactivateSize(Long sizeId);
    
    List<BrandSize> getAllSizes();
    Bill findById(Long id);
    Bill getBill(Long id);
    
}