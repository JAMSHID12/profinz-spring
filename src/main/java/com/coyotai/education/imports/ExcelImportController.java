package com.coyotai.education.imports;

import com.coyotai.education.common.ApiResponse;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('ADMINISTRATIVE')")
public class ExcelImportController {
    private final ExcelImportService service;
    public ExcelImportController(ExcelImportService service) { this.service=service; }
    @GetMapping("/{kind}/template")
    public ResponseEntity<byte[]> template(@PathVariable String kind) {
        ImportKind type=ImportKind.parse(kind);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=profinz-"+type.name().toLowerCase(java.util.Locale.ROOT)+"-template.xlsx")
                .cacheControl(CacheControl.noStore()).body(service.template(type));
    }
    @PostMapping(value="/{kind}/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExcelImportService.Result> preview(@PathVariable String kind,@RequestPart("file") MultipartFile file, @RequestParam(required=false) Long batchId) {
        return ApiResponse.ok(service.preview(ImportKind.parse(kind),file,batchId));
    }
    @PostMapping(value="/{kind}/save",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExcelImportService.Result> save(@PathVariable String kind,@RequestPart("file") MultipartFile file,@RequestParam String digest, @RequestParam(required=false) Long batchId) {
        return ApiResponse.ok(service.save(ImportKind.parse(kind),file,digest,batchId));
    }
}
