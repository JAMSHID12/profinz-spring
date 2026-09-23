package com.coyotai.education.student.portal;

import com.coyotai.education.assessment.AcademicTest;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.discipline.DisciplineDtos.FineResponse;
import com.coyotai.education.discipline.DisciplineDtos.RecordResponse;
import com.coyotai.education.fee.FeeDtos.FeeSummary;
import com.coyotai.education.notification.NotificationDtos.Inbox;
import com.coyotai.education.performance.PerformanceService.StudentPerformance;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.progress.ProgressCardService.Card;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import com.coyotai.education.student.StudentDtos.StudentDetail;
import com.coyotai.education.student.portal.StudentPortalDtos.Dashboard;
import com.coyotai.education.student.portal.StudentPortalDtos.MyAttendance;
import com.coyotai.education.student.portal.StudentPortalDtos.MyExams;
import com.coyotai.education.student.portal.StudentPortalDtos.TestResult;
import com.coyotai.education.syllabus.SyllabusService.BatchSyllabus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Student self-service. Every endpoint is read-only and resolves the student from the
 * authenticated account - there is no student id in any of these URLs.
 */
@RestController
@RequestMapping("/api/students/me")
@RequiresModule(ModuleCode.STUDENT_PORTAL)
public class StudentPortalController {

    private final StudentPortalService portalService;

    public StudentPortalController(StudentPortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MY_PROFILE_VIEW')")
    public ApiResponse<StudentDetail> profile() {
        return ApiResponse.ok(portalService.profile());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('MY_PROFILE_VIEW')")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(portalService.dashboard());
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('MY_SCHEDULE_VIEW')")
    public ApiResponse<List<ScheduleResponse>> schedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(portalService.schedule(from, to));
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('MY_ATTENDANCE_VIEW')")
    public ApiResponse<MyAttendance> attendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(portalService.attendance(from, to));
    }

    @GetMapping("/tests")
    @PreAuthorize("hasAuthority('MY_TEST_RESULTS_VIEW')")
    public ApiResponse<List<TestResult>> tests(@RequestParam(required = false) AcademicTest.Type type) {
        return ApiResponse.ok(portalService.tests(type));
    }

    @GetMapping("/exams")
    @PreAuthorize("hasAuthority('MY_EXAM_RESULTS_VIEW')")
    public ApiResponse<MyExams> exams() {
        return ApiResponse.ok(portalService.exams());
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAuthority('MY_PERFORMANCE_VIEW')")
    public ApiResponse<StudentPerformance> performance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(portalService.performance(from, to));
    }

    @GetMapping("/progress-cards")
    @PreAuthorize("hasAuthority('MY_PROGRESS_CARD_VIEW')")
    public ApiResponse<List<Card>> progressCards() {
        return ApiResponse.ok(portalService.progressCards());
    }

    @GetMapping("/progress-cards/{id}")
    @PreAuthorize("hasAuthority('MY_PROGRESS_CARD_VIEW')")
    public ApiResponse<Card> progressCard(@PathVariable Long id) {
        return ApiResponse.ok(portalService.progressCard(id));
    }

    @GetMapping("/discipline")
    @PreAuthorize("hasAuthority('MY_DISCIPLINE_VIEW')")
    public ApiResponse<List<RecordResponse>> discipline() {
        return ApiResponse.ok(portalService.discipline());
    }

    @GetMapping("/fines")
    @PreAuthorize("hasAuthority('MY_FINE_VIEW')")
    public ApiResponse<List<FineResponse>> fines() {
        return ApiResponse.ok(portalService.fines());
    }

    @GetMapping("/fees")
    @PreAuthorize("hasAuthority('MY_FEE_VIEW')")
    @RequiresModule(ModuleCode.FEES)
    public ApiResponse<FeeSummary> fees() {
        return ApiResponse.ok(portalService.fees());
    }

    @GetMapping("/syllabus")
    @PreAuthorize("hasAuthority('MY_SYLLABUS_VIEW')")
    public ApiResponse<BatchSyllabus> syllabus() {
        return ApiResponse.ok(portalService.syllabus());
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('MY_NOTIFICATION_VIEW')")
    public ApiResponse<Inbox> notifications() {
        return ApiResponse.ok(portalService.notifications());
    }

    @PostMapping("/notifications/read")
    @PreAuthorize("hasAuthority('MY_NOTIFICATION_VIEW')")
    public ApiResponse<Void> markRead() {
        portalService.markNotificationsRead();
        return ApiResponse.ok(null);
    }
}
