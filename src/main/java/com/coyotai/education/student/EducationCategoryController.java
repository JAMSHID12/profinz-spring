package com.coyotai.education.student;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/education-categories")
@RequiresModule(ModuleCode.ACADEMICS)
public class EducationCategoryController {
    private final EducationCategoryService service;
    public EducationCategoryController(EducationCategoryService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAnyAuthority('MASTER_DATA_MANAGE', 'STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_VIEW', 'ASSIGNED_STUDENT_VIEW', 'SYLLABUS_VIEW', 'SYLLABUS_MANAGE', 'SCHEDULE_CREATE', 'SCHEDULE_UPDATE')")
    public ApiResponse<List<EducationCategoryService.Response>> list() { return ApiResponse.ok(service.list()); }
    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<EducationCategoryService.Response> create(@Valid @RequestBody EducationCategoryService.Request request) {
        return ApiResponse.ok(service.save(null, request), "Education category created");
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<EducationCategoryService.Response> update(@PathVariable Long id, @Valid @RequestBody EducationCategoryService.Request request) {
        return ApiResponse.ok(service.save(id, request), "Education category updated");
    }
}
