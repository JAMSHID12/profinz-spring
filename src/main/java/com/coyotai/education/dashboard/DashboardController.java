package com.coyotai.education.dashboard;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.dashboard.DashboardDtos.AdminAttendanceDashboard;
import com.coyotai.education.dashboard.DashboardDtos.FacultyDashboard;
import com.coyotai.education.dashboard.DashboardDtos.MentorDashboard;
import com.coyotai.education.dashboard.DashboardDtos.StudentAttendanceDetail;
import com.coyotai.education.dashboard.DashboardDtos.Summary;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Home screens. Students use /api/students/me/dashboard. */
@RestController
@RequestMapping("/api/dashboard")
@RequiresModule(ModuleCode.ACADEMICS)
public class DashboardController {

    private final DashboardService dashboardService;
    private final AttendanceInsightsService insightsService;

    public DashboardController(DashboardService dashboardService, AttendanceInsightsService insightsService) {
        this.dashboardService = dashboardService;
        this.insightsService = insightsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public ApiResponse<Summary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW') and hasRole('MENTORS')")
    public ApiResponse<MentorDashboard> mentor() {
        return ApiResponse.ok(dashboardService.mentor());
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW') and hasRole('FACULTY')")
    public ApiResponse<FacultyDashboard> faculty() {
        return ApiResponse.ok(dashboardService.faculty());
    }

    /**
     * The administrator's attendance and discipline dashboard for a period (default: the last 30
     * days), optionally for one course, batch or mentor, with the previous period to compare.
     * Administrators only - no other role has this dashboard.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW') and hasRole('ADMINISTRATIVE')")
    public ApiResponse<AdminAttendanceDashboard> admin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long mentorId,
            @RequestParam(defaultValue = "false") boolean compare) {
        return ApiResponse.ok(insightsService.adminDashboard(from, to, courseId, batchId, mentorId, compare));
    }

    /** One student's attendance for a period: totals, the day-by-day calendar and every mark. Administrators only. */
    @GetMapping("/attendance/students/{studentId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') and hasRole('ADMINISTRATIVE')")
    public ApiResponse<StudentAttendanceDetail> studentAttendance(
            @PathVariable Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(insightsService.student(studentId, from, to));
    }
}
