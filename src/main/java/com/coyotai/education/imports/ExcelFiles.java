package com.coyotai.education.imports;

import com.coyotai.education.common.BusinessRuleException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Bounded XLSX reader. Never evaluates formulas and never accepts arbitrary column mappings. */
@Component
public class ExcelFiles {
    public static final int MAX_ROWS = 200;
    public record RowData(int row, Map<String, String> values) { }
    public List<RowData> read(ImportKind kind, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new BusinessRuleException("Upload a non-empty .xlsx file, up to 2 MB");
        try {
            byte[] bytes = file.getBytes();
            checkArchive(bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                if (workbook.isMacroEnabled()) throw new BusinessRuleException("Macros are not supported");
                Sheet sheet = workbook.getSheet("Data");
                if (sheet == null || sheet.getRow(0) == null) throw new BusinessRuleException("Use the downloaded template with its Data sheet and original headers");
                Row header = sheet.getRow(0);
                if (header.getLastCellNum() != kind.headers.size()) throw new BusinessRuleException("Column count differs from the template");
                for (int c = 0; c < kind.headers.size(); c++)
                    if (!kind.headers.get(c).equals(value(header.getCell(c))))
                        throw new BusinessRuleException("Column " + (c + 1) + " must be " + kind.headers.get(c) + ". Download the correct template.");
                if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessRuleException("Maximum 200 data rows per upload. Remove unused rows.");
                List<RowData> rows = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    if (row.getLastCellNum() > kind.headers.size()) throw new BusinessRuleException("Unexpected extra column at row " + (i + 1));
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int c = 0; c < kind.headers.size(); c++) values.put(kind.headers.get(c), value(row.getCell(c)));
                    if (values.values().stream().anyMatch(v -> !v.isBlank())) rows.add(new RowData(i + 1, values));
                }
                if (rows.isEmpty()) throw new BusinessRuleException("The Data sheet is empty. Add your records below the headers; the Example sheet is not imported.");
                return rows;
            }
        } catch (BusinessRuleException ex) { throw ex; }
        catch (Exception ex) { throw new BusinessRuleException("Cannot read this workbook. Upload an unencrypted .xlsx using the downloaded template."); }
    }
    private String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
            throw new BusinessRuleException("Formulas and error cells are not allowed. Paste values only.");
        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> "";
        };
        if (value.length() > 1000) throw new BusinessRuleException("A cell exceeds the 1000-character limit");
        return value;
    }
    private void checkArchive(byte[] bytes) throws IOException {
        long total = 0; int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 200) throw new BusinessRuleException("Workbook is too complex; use the template");
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > 10 * 1024 * 1024) throw new BusinessRuleException("Expanded workbook exceeds 10 MB; use a smaller file");
                }
            }
        }
    }
    public byte[] template(ImportKind kind, List<List<String>> lookups) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            table(workbook, "Data", kind.headers, List.of());
            table(workbook, "Example", kind.headers, List.of(kind.example));
            table(workbook, "Instructions", List.of("Rule", "Details"), List.of(
                    List.of("Import type", kind.label),
                    List.of("Format", "Only Data is imported. Keep exact headers and order. Examples are fictional; replace with your records."),
                    List.of("Limits", "XLSX only, 2 MB maximum, 200 rows. No formulas or macros. Blank rows are ignored."),
                    List.of("Dates and phones", "Dates: YYYY-MM-DD. Phone numbers: text with country code. Keep leading zeros and +."),
                    List.of("Students", "Required: full_name, parent_name, parent_phone. Choose a batch by name on the upload screen; all students in this workbook join that batch. Use a separate upload for each batch. IDs are generated. Parent messaging consent and student login are managed later in the student profile."),
                    List.of("Staff", "Required: full_name, employee_code, username, password (8-72 characters). Faculty type: FULL_TIME or GUEST. Login passwords must be changed at first sign-in."),
                    List.of("Fee structure", "Required: course_id, fee_amount (non-negative, up to 2 decimals), default_installments (1-36). Updates existing course defaults only; existing student plans are unchanged."),
                    List.of("Saving", "Validate, review, then Save. Every row must be valid. The whole upload saves together or nothing saves."),
                    List.of("Duplicates", "Students: same name + batch + parent phone are rejected. Staff: existing username or employee code rejected. One fee row per course."),
                    List.of("Privacy", "Passwords never appear in previews. Store filled staff workbooks privately and remove them when no longer needed.")));
            if (!lookups.isEmpty()) table(workbook, "Reference IDs", List.of("ID", "Name", "Details"), lookups);
            workbook.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessRuleException("Could not generate template"); }
    }
    private void table(XSSFWorkbook workbook, String name, List<String> headers, List<List<String>> data) {
        Sheet sheet = workbook.createSheet(name); sheet.createFreezePane(0, 1);
        CellStyle heading = workbook.createCellStyle(); heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); heading.setFont(font);
        CellStyle text = workbook.createCellStyle(); text.setDataFormat(workbook.createDataFormat().getFormat("@")); text.setWrapText(true);
        Row row = sheet.createRow(0); row.setHeightInPoints(28);
        for (int c=0;c<headers.size();c++) { Cell cell=row.createCell(c); cell.setCellValue(headers.get(c)); cell.setCellStyle(heading);
            sheet.setColumnWidth(c, (name.equals("Instructions") && c==1 ? 110 : 26)*256); sheet.setDefaultColumnStyle(c,text); }
        for(int i=0;i<data.size();i++) { row=sheet.createRow(i+1); row.setHeightInPoints(name.equals("Instructions") ? 45 : 34);
            for(int c=0;c<data.get(i).size();c++) { Cell cell=row.createCell(c);cell.setCellValue(data.get(i).get(c));cell.setCellStyle(text); } }
    }
}
