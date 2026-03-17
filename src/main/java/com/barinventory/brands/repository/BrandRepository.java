package com.barinventory.brands.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.barinventory.brands.entity.Brand;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    // ── Existence checks ──────────────────────────────────────────────
    boolean existsByBrandNameIgnoreCase(String brandName);
    boolean existsByBrandCodeIgnoreCase(String brandCode);

    // ── Find by code (for uniqueness validation on update) ────────────
    Optional<Brand> findByBrandCodeIgnoreCase(String brandCode);

    // ── Active brands ─────────────────────────────────────────────────
    List<Brand> findByActiveTrue();
    List<Brand> findByCategoryAndActiveTrue(Brand.Category category);

    // ── Single brand with sizes (avoids N+1) ──────────────────────────
    @Query("""
           SELECT DISTINCT b FROM Brand b
           LEFT JOIN FETCH b.sizes s
           WHERE b.id = :id
           """)
    Optional<Brand> findByIdWithSizes(@Param("id") Long id);

    @Query("""
    		SELECT DISTINCT b FROM Brand b
    		LEFT JOIN FETCH b.sizes s
    		WHERE b.active = true AND (s IS NULL OR s.active = true)
    		ORDER BY b.brandName
    		""")
    		List<Brand> findAllActiveWithActiveSizes();
    
    @Query("""
    	       SELECT DISTINCT b FROM Brand b
    	       LEFT JOIN FETCH b.sizes s
    	       WHERE b.active = true AND (s IS NULL OR s.active = true)
    	       ORDER BY b.brandName
    	       """)
    	List<Brand> findAllActiveWithSizes();
    
}