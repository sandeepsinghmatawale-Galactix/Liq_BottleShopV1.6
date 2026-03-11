package com.barinventory.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.barinventory.billing.entity.Bill;

public interface BillRepository extends JpaRepository<Bill, Long> {

	@Query("SELECT b FROM Bill b JOIN FETCH b.items WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
	List<Bill> findByUserIdWithItems(Long userId);
	
	
	 // Fetch bills with items (avoid LazyInitializationException)
    @Query("SELECT DISTINCT b FROM Bill b LEFT JOIN FETCH b.items WHERE b.user.id = :userId")
    List<Bill> findByUserIdWithItems2(Long userId);

    // Admin reporting (optional future)
    List<Bill> findByUserId(Long userId);
    
    
 // Find all bills by user (for "My Bills" page)
    List<Bill> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Find all finalized bills
    List<Bill> findByFinalizedTrue();
	
}