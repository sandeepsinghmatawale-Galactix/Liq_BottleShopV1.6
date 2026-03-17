package com.barinventory.invoice.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

@Service
public class TableExtractionService {

	public List<List<String>> extractTable(File pdfFile) {
		List<List<String>> rows = new ArrayList<>();

		try (PDDocument document = PDDocument.load(pdfFile)) {

			ObjectExtractor extractor = new ObjectExtractor(document);
			PageIterator pages = extractor.extract();

			while (pages.hasNext()) {
				Page page = pages.next();

				SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();
				List<Table> tables = algo.extract(page);

				for (Table table : tables) {
					for (List<RectangularTextContainer> row : table.getRows()) {

						List<String> cells = new ArrayList<>();

						for (RectangularTextContainer cell : row) {
							cells.add(cell.getText().trim());
						}

						rows.add(cells);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return rows;
	}
}