package com.coyotai.education.report;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.report.ReportService.AcademicReport;
import com.coyotai.education.report.ReportService.AttendanceReport;
import com.coyotai.education.report.ReportService.FeeReport;
import com.coyotai.education.report.ReportService.NotificationReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiresModule(ModuleCode.REPORTS)
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ApiResponse<AttendanceReport> attendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(reportService.attendance(from, to, batchId));
    }

    @GetMapping("/fees")
    @PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('FEE_VIEW')")
    @RequiresModule(ModuleCode.FEES)
    public ApiResponse<FeeReport> fees(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.fees(from, to));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('NOTIFICATION_VIEW')")
    @RequiresModule(ModuleCode.NOTIFICATIONS)
    public ApiResponse<NotificationReport> notifications(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.notifications(from, to));
    }

    @GetMapping("/academic")
    @PreAuthorize("hasAuthority('REPORT_VIEW') and hasAuthority('PERFORMANCE_VIEW')")
    public ApiResponse<AcademicReport> academic(
            @RequestParam Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.academic(batchId, from, to));
    }
}
