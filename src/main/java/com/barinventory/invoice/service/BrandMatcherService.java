package com.barinventory.invoice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.barinventory.brands.repository.BrandRepository;
import com.barinventory.invoice.dto.ExtractedItemData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fuzzy matches brand names extracted from ICDC PDF against your existing brand
 * master list.
 *
 * ICDC PDFs often have truncated or differently spelled brand names: PDF says :
 * "OFFICERS CHOICE WHISKY" Master has: "Officer's Choice Whisky"
 *
 * Uses Levenshtein distance algorithm. Score >= 85% → auto matched,
 * matchConfident = true Score < 85% → suggested but flagged, owner must confirm
 * on review screen
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BrandMatcherService {
	
	private final BrandRepository brandRepository;

	private static final double AUTO_MATCH_THRESHOLD = 0.85;

	/**
	 * Lightweight reference to one BrandSize row from your brand module.
	 * InvoiceController builds this list from BrandService.getAllActiveBrands() and
	 * passes it to InvoiceService.uploadAndExtract().
	 *
	 * id = BrandSize.id → saved as brandMasterId in InvoiceItem brandName =
	 * Brand.brandName → matched against PDF brand name sizeMl = BrandSize.volumeMl
	 * → used as size filter during matching
	 */
	public record BrandMasterRef(Long id, String brandName, Integer sizeMl) {
	}

	/**
	 * Matches extracted item's brandNameRaw against master brand list. Mutates item
	 * directly — sets brandNameMatched, brandMasterId, matchConfident.
	 *
	 * Size used as secondary filter — if both item and master have sizeMl, only
	 * brands with matching size are considered.
	 */
	public void matchBrand(ExtractedItemData item, List<BrandMasterRef> masterBrands) {

		if (item.getBrandNameRaw() == null || masterBrands == null || masterBrands.isEmpty()) {
			item.setMatchConfident(false);
			return;
		}

		String rawNormalized = normalize(item.getBrandNameRaw());

		BrandMasterRef bestMatch = null;
		double bestScore = 0.0;

		for (BrandMasterRef master : masterBrands) {

			// Size filter — skip if sizes are both present but don't match
			if (item.getSizeMl() != null && master.sizeMl() != null && !item.getSizeMl().equals(master.sizeMl())) {
				continue;
			}

			String masterNormalized = normalize(master.brandName());
			double score = similarity(rawNormalized, masterNormalized);

			if (score > bestScore) {
				bestScore = score;
				bestMatch = master;
			}
		}

		if (bestMatch != null && bestScore >= AUTO_MATCH_THRESHOLD) {
			// High confidence — auto matched
			item.setBrandNameMatched(bestMatch.brandName());
			item.setBrandMasterId(bestMatch.id());
			item.setMatchConfident(true);
			log.debug("Auto matched: '{}' → '{}' (score: {})", item.getBrandNameRaw(), bestMatch.brandName(),
					String.format("%.2f", bestScore));

		} else if (bestMatch != null) {
			// Low confidence — suggest but flag for owner to confirm
			item.setBrandNameMatched(bestMatch.brandName());
			item.setBrandMasterId(bestMatch.id());
			item.setMatchConfident(false);
			log.warn("Low confidence: '{}' → '{}' (score: {}) — needs owner confirmation", item.getBrandNameRaw(),
					bestMatch.brandName(), String.format("%.2f", bestScore));

		} else {
			// No match at all
			item.setBrandNameMatched(null);
			item.setBrandMasterId(null);
			item.setMatchConfident(false);
			log.warn("No match found for brand: '{}'", item.getBrandNameRaw());
		}
	}
	
	 
	public List<BrandMatcherService.BrandMasterRef> getAllBrandRefs() {

	    return brandRepository.findAllActiveWithSizes()
	            .stream()
	            .flatMap(brand -> brand.getSizes().stream()
	                    .filter(size -> size.isActive())
	                    .map(size -> new BrandMatcherService.BrandMasterRef(
	                            size.getId(),
	                            brand.getBrandName(),
	                            size.getVolumeMl() // or sizeMl based on your entity
	                    ))
	            )
	            .toList();
	}
	
	 

	// ── String Utilities ──────────────────────────────────────────────────────

	/**
	 * Normalizes brand name before comparison. "Officer's Choice" → "OFFICERS
	 * CHOICE" "KING FISHER" → "KING FISHER"
	 */
	private String normalize(String input) {
		if (input == null)
			return "";
		return input.toUpperCase().replaceAll("[^A-Z0-9\\s]", "") // remove punctuation
				.replaceAll("\\s+", " ") // collapse spaces
				.trim();
	}

	/**
	 * Levenshtein similarity — returns score 0.0 to 1.0. 1.0 = identical, 0.0 =
	 * completely different.
	 */
	private double similarity(String s1, String s2) {
		if (s1.equals(s2))
			return 1.0;
		if (s1.isEmpty() || s2.isEmpty())
			return 0.0;
		int distance = levenshteinDistance(s1, s2);
		int maxLen = Math.max(s1.length(), s2.length());
		return 1.0 - ((double) distance / maxLen);
	}

	private int levenshteinDistance(String s1, String s2) {
		int m = s1.length();
		int n = s2.length();
		int[][] dp = new int[m + 1][n + 1];

		for (int i = 0; i <= m; i++)
			dp[i][0] = i;
		for (int j = 0; j <= n; j++)
			dp[0][j] = j;

		for (int i = 1; i <= m; i++) {
			for (int j = 1; j <= n; j++) {
				if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
					dp[i][j] = dp[i - 1][j - 1];
				} else {
					dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
				}
			}
		}
		return dp[m][n];
	}
}