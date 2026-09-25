package com.coyotai.education.imports;
import com.coyotai.education.common.BusinessRuleException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ExcelFilesTest {
    private final ExcelFiles files = new ExcelFiles();
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file","test.xlsx","application/octet-stream",bytes); }
    private byte[] workbook(ImportKind kind, java.util.function.Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(files.template(kind,List.of())));var out=new ByteArrayOutputStream()) {
            edit.accept(book); book.write(out);return out.toByteArray();
        }
    }
    @Test void studentTemplateContainsOnlyUserFacingFields() {
        assertThat(ImportKind.STUDENTS.headers).containsExactly("full_name","parent_name","parent_phone","mobile","email","date_of_birth","admission_date");
    }
    @Test void allTemplatesRoundTripOnlyDataSheet() throws Exception {
        for(var kind:ImportKind.values()) {
            byte[] bytes=workbook(kind,b->{var row=b.getSheet("Data").createRow(1);for(int i=0;i<kind.example.size();i++)row.createCell(i).setCellValue(kind.example.get(i));});
            var rows=files.read(kind,upload(bytes));assertThat(rows).hasSize(1);assertThat(rows.get(0).row()).isEqualTo(2);
            assertThat(rows.get(0).values().keySet()).containsExactlyElementsOf(kind.headers);
        }
    }
    @Test void examplesCannotAccidentallyBeImported() {
        assertThatThrownBy(()->files.read(ImportKind.STUDENTS,upload(files.template(ImportKind.STUDENTS,List.of()))))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("empty");
    }
    @Test void rejectsChangedHeaderAndFormulaAndTooManyRows() throws Exception {
        byte[] header=workbook(ImportKind.FEES,b->b.getSheet("Data").getRow(0).getCell(0).setCellValue("other_id"));
        assertThatThrownBy(()->files.read(ImportKind.FEES,upload(header))).hasMessageContaining("course_id");
        byte[] formula=workbook(ImportKind.FEES,b->b.getSheet("Data").createRow(1).createCell(0).setCellFormula("1+1"));
        assertThatThrownBy(()->files.read(ImportKind.FEES,upload(formula))).hasMessageContaining("Formulas");
        byte[] large=workbook(ImportKind.FEES,b->b.getSheet("Data").createRow(201).createCell(0).setCellValue("1"));
        assertThatThrownBy(()->files.read(ImportKind.FEES,upload(large))).hasMessageContaining("200");
    }
    @Test void rejectsWrongFormatAndOversizedUpload() {
        assertThatThrownBy(()->files.read(ImportKind.FEES,new MockMultipartFile("file","test.xls","",new byte[]{1}))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->files.read(ImportKind.FEES,upload(new byte[2*1024*1024+1]))).isInstanceOf(BusinessRuleException.class);
    }
}
