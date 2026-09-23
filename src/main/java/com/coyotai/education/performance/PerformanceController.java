package com.coyotai.education.performance;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.performance.PerformanceService.BatchRow;
import com.coyotai.education.performance.PerformanceService.StudentPerformance;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RequiresModule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/performance")
@RequiresModule(ModuleCode.ACADEMICS)
public class PerformanceController {

    private final PerformanceService performanceService;
    private final ProjectConfigService configService;

    public PerformanceController(PerformanceService performanceService, ProjectConfigService configService) {
        this.performanceService = performanceService;
        this.configService = configService;
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW')")
    public ApiResponse<StudentPerformance> student(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireEnabled();
        return ApiResponse.ok(performanceService.forStudentAsStaff(id, from, to));
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW')")
    public ApiResponse<List<BatchRow>> batch(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireEnabled();
        return ApiResponse.ok(performanceService.forBatch(id, from, to));
    }

    private void requireEnabled() {
        if (!configService.academics().getPerformance().isEnabled()) {
            throw new BusinessRuleException("Performance tracking is switched off for this client");
        }
    }
}
