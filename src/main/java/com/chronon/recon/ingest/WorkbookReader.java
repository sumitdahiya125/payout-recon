package com.chronon.recon.ingest;

import com.chronon.recon.exception.ColumnMappingException;
import com.chronon.recon.exception.IngestionException;
import com.chronon.recon.exception.ReconciliationException;
import com.chronon.recon.ingest.mapping.ColumnMapping;
import com.chronon.recon.ingest.mapping.ColumnResolver;
import com.chronon.recon.ingest.mapping.MappingProfile;
import com.chronon.recon.ingest.normalizer.TextNormalizer;
import com.chronon.recon.domain.quality.DataQualityLog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an uploaded workbook into a {@link RawTable}: right sheet, right header row, typed cells.
 *
 * <p>This is the layer that absorbs real-world spreadsheet mess: preamble rows, merged header
 * cells, formula cells, numbers stored as text, and sheets whose data starts anywhere.</p>
 */
@Component
public class WorkbookReader {

    private static final Logger log = LoggerFactory.getLogger(WorkbookReader.class);
    private static final int MAX_DATA_ROWS = 200_000;

    private final ColumnResolver resolver;

    public WorkbookReader(ColumnResolver resolver) {
        this.resolver = resolver;
    }

    public IngestedTable read(InputStream input, MappingProfile profile, DataQualityLog quality) {
        return readAuto(input, List.of(profile), quality);
    }

    public IngestedTable readAuto(InputStream input, List<MappingProfile> candidateProfiles, DataQualityLog quality) {
        if (candidateProfiles == null || candidateProfiles.isEmpty()) {
            throw new IllegalArgumentException("No mapping profiles provided for ingestion.");
        }
        try (Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            MappingProfile bestProfile = null;
            Sheet bestSheet = null;
            ColumnResolver.HeaderCandidate bestHeader = null;
            List<List<String>> bestCandidateHeaders = null;
            int bestWidth = 0;
            List<CellRangeAddress> bestMerged = null;
            double bestRank = -1.0;
            List<String> lastMissing = null;

            for (MappingProfile profile : candidateProfiles) {
                Sheet sheet = selectSheet(workbook, profile, null);
                if (sheet == null) {
                    continue;
                }
                List<CellRangeAddress> merged = sheet.getMergedRegions();
                int scanLast = Math.min(sheet.getLastRowNum(), profile.scanRows() - 1);
                int width = detectWidth(sheet, scanLast, merged, formatter, evaluator);
                if (width == 0) {
                    continue;
                }
                List<List<String>> candidateHeaders = new ArrayList<>();
                for (int rowIndex = 0; rowIndex <= scanLast; rowIndex++) {
                    candidateHeaders.add(rowTexts(sheet, rowIndex, width, merged, formatter, evaluator));
                }

                ColumnResolver.HeaderCandidate header = resolver.bestHeader(candidateHeaders, profile);
                if (header.mapping().usable() && header.rank() > bestRank) {
                    bestRank = header.rank();
                    bestProfile = profile;
                    bestSheet = sheet;
                    bestHeader = header;
                    bestCandidateHeaders = candidateHeaders;
                    bestWidth = width;
                    bestMerged = merged;
                } else if (bestProfile == null && !header.mapping().missingRequired().isEmpty()) {
                    lastMissing = header.mapping().missingRequired();
                }
            }

            if (bestProfile == null || bestHeader == null) {
                String targetLabel = candidateProfiles.get(0).target().label();
                String missingDesc = lastMissing != null ? String.join(", ", lastMissing) : "required columns";
                throw new ColumnMappingException("Could not automatically map " + targetLabel
                        + " columns. None of the supported profiles matched. Missing fields: " + missingDesc);
            }

            ColumnMapping mapping = bestHeader.mapping();
            int headerRowNumber = bestHeader.index() + 1;
            log.info("Auto-detected profile '{}' for {} (matched header row {} of sheet '{}'): {}",
                    bestProfile.id(), bestProfile.target().label(), headerRowNumber, bestSheet.getSheetName(),
                    mapping.describe());

            for (String warning : mapping.warnings()) {
                quality.warn(bestProfile.target().label(), headerRowNumber, "columns", warning);
            }
            quality.info(bestProfile.target().label(), headerRowNumber, "columns",
                    "Auto-detected format using profile '" + bestProfile.id() + "' (mapped "
                            + mapping.columns().size() + " of " + bestProfile.fields().size() + " fields).");

            List<String> headers = bestCandidateHeaders.get(bestHeader.index());
            List<RawRow> rows = new ArrayList<>();
            int lastRow = bestSheet.getLastRowNum();
            int blankRows = 0;
            for (int rowIndex = bestHeader.index() + 1; rowIndex <= lastRow; rowIndex++) {
                RawRow row = readRow(bestSheet, rowIndex, bestWidth, bestMerged, formatter, evaluator);
                if (row.isBlank()) {
                    blankRows++;
                    continue;
                }
                rows.add(row);
                if (rows.size() >= MAX_DATA_ROWS) {
                    throw new IngestionException("Sheet '" + bestSheet.getSheetName()
                            + "' exceeds the maximum supported DOM capacity of " + MAX_DATA_ROWS
                            + " data rows. For 500,000+ rows, use the SAX streaming pipeline.");
                }
            }
            if (blankRows > 0) {
                quality.info(bestProfile.target().label(), 0, null,
                        "Ignored " + blankRows + " blank row(s) in sheet '" + bestSheet.getSheetName() + "'.");
            }

            return new IngestedTable(new RawTable(bestSheet.getSheetName(), headerRowNumber, headers, rows), mapping, bestProfile);
        } catch (ReconciliationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new IngestionException("Could not read the workbook: " + ex.getMessage(), ex);
        }
    }

    private Sheet selectSheet(Workbook workbook, MappingProfile profile, DataQualityLog quality) {
        String wanted = profile.sheetNameContains();
        if (wanted != null && !wanted.isBlank()) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String name = workbook.getSheetName(i);
                if (name.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT))) {
                    return workbook.getSheetAt(i);
                }
            }
            if (quality != null) {
                quality.warn(profile.target().label(), 0, "sheet",
                        "No sheet name contains '" + wanted + "'; using the first sheet with data.");
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (!workbook.isSheetHidden(i) && workbook.getSheetAt(i).getPhysicalNumberOfRows() > 0) {
                return workbook.getSheetAt(i);
            }
        }
        return workbook.getSheetAt(0);
    }

    private int detectWidth(Sheet sheet, int scanLast, List<CellRangeAddress> merged,
                            DataFormatter formatter, FormulaEvaluator evaluator) {
        int width = 0;
        for (int rowIndex = 0; rowIndex <= scanLast; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            width = Math.max(width, row.getLastCellNum());
        }
        return Math.max(width, 0);
    }

    private List<String> rowTexts(Sheet sheet, int rowIndex, int width, List<CellRangeAddress> merged,
                                  DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> texts = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            texts.add(TextNormalizer.clean(readCell(sheet, rowIndex, column, merged, formatter, evaluator).asText()));
        }
        return texts;
    }

    private RawRow readRow(Sheet sheet, int rowIndex, int width, List<CellRangeAddress> merged,
                           DataFormatter formatter, FormulaEvaluator evaluator) {
        List<RawCell> cells = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            cells.add(readCell(sheet, rowIndex, column, merged, formatter, evaluator));
        }
        return new RawRow(rowIndex + 1, cells);
    }

    private RawCell readCell(Sheet sheet, int rowIndex, int column, List<CellRangeAddress> merged,
                             DataFormatter formatter, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return RawCell.blank();
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return RawCell.blank();
        }
        Cell source = mergedValue(sheet, rowIndex, column, merged, cell);

        CellType type = source.getCellType();
        if (type == CellType.FORMULA) {
            try {
                CellValue value = evaluator.evaluate(source);
                if (value == null) {
                    return RawCell.blank();
                }
                return switch (value.getCellType()) {
                    case NUMERIC -> numericCell(source, value.getNumberValue(), formatter, evaluator);
                    case STRING -> RawCell.ofText(value.getStringValue());
                    case BOOLEAN -> RawCell.ofText(String.valueOf(value.getBooleanValue()));
                    default -> RawCell.blank();
                };
            } catch (RuntimeException ex) {
                return RawCell.ofText(formatter.formatCellValue(source));
            }
        }
        return switch (type) {
            case NUMERIC -> numericCell(source, source.getNumericCellValue(), formatter, evaluator);
            case STRING -> RawCell.ofText(source.getStringCellValue());
            case BOOLEAN -> RawCell.ofText(String.valueOf(source.getBooleanCellValue()));
            case ERROR -> RawCell.ofText(formatter.formatCellValue(source));
            default -> RawCell.blank();
        };
    }

    private RawCell numericCell(Cell cell, double value, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (DateUtil.isCellDateFormatted(cell)) {
            try {
                return RawCell.ofDate(cell.getLocalDateTimeCellValue().toLocalDate());
            } catch (RuntimeException ex) {
                log.debug("Date-formatted cell could not be read as a date, falling back to numeric", ex);
            }
        }
        String text = formatter.formatCellValue(cell, evaluator);
        return RawCell.ofNumber(BigDecimal.valueOf(value), text);
    }

    /** A merged region's value lives in its top-left cell; every other cell in the region is blank. */
    private Cell mergedValue(Sheet sheet, int rowIndex, int column, List<CellRangeAddress> merged, Cell fallback) {
        for (CellRangeAddress region : merged) {
            if (region.isInRange(rowIndex, column)
                    && (region.getFirstRow() != rowIndex || region.getFirstColumn() != column)) {
                Row topRow = sheet.getRow(region.getFirstRow());
                if (topRow == null) {
                    return fallback;
                }
                Cell topLeft = topRow.getCell(region.getFirstColumn());
                return topLeft == null ? fallback : topLeft;
            }
        }
        return fallback;
    }

    private String buildMappingError(MappingProfile profile, ColumnResolver.HeaderCandidate header,
                                     List<List<String>> candidates) {
        StringBuilder message = new StringBuilder();
        message.append("Could not identify the required column(s) for profile '")
                .append(profile.id()).append("' (").append(profile.target().label()).append(").");
        if (!header.mapping().missingRequired().isEmpty()) {
            message.append(" Missing: ").append(String.join(", ", header.mapping().missingRequired())).append(".");
        }
        int rowIndex = Math.max(0, Math.min(header.index(), candidates.size() - 1));
        List<String> row = candidates.isEmpty() ? List.of() : candidates.get(rowIndex);
        message.append(" Best candidate header row ").append(rowIndex + 1).append(" contains: ");
        message.append(row.stream().filter(s -> !s.isBlank()).limit(15).toList());
        message.append(". Either upload the correct file or add/adjust aliases in profiles/")
                .append(profile.id()).append(".yml.");
        return message.toString();
    }
}
