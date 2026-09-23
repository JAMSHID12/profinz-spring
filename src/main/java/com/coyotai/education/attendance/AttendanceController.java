package com.coyotai.education.attendance;

import com.coyotai.education.attendance.AttendanceDtos.BulkRequest;
import com.coyotai.education.attendance.AttendanceDtos.BulkResult;
import com.coyotai.education.attendance.AttendanceDtos.Record;
import com.coyotai.education.attendance.AttendanceDtos.Sheet;
import com.coyotai.education.attendance.AttendanceDtos.TakerClass;
import com.coyotai.education.attendance.AttendanceDtos.UpdateRequest;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.common.PageResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance. Taking and correcting it needs the permission AND the mentor or faculty role;
 * the service then checks it is the user's own batch or class. Viewing needs ATTENDANCE_VIEW.
 */
@RestController
@RequestMapping("/api/attendance")
@RequiresModule(ModuleCode.ACADEMICS)
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ProjectConfigService configService;

    public AttendanceController(AttendanceService attendanceService, ProjectConfigService configService) {
        this.attendanceService = attendanceService;
        this.configService = configService;
    }

    /** The whole-day registers and classes the signed-in mentor or faculty member takes on a date. */
    @GetMapping("/classes")
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE') and hasAnyRole('MENTORS', 'FACULTY')")
    public ApiResponse<List<TakerClass>> classes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(attendanceService.classesToTake(date == null ? configService.today() : date));
    }

    /** Marking sheet: every active student of the batch plus existing marks. */
    @GetMapping("/sheet")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ApiResponse<Sheet> sheet(@RequestParam Long batchId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(required = false) Long scheduleId) {
        return ApiResponse.ok(attendanceService.sheet(batchId, date, scheduleId));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE') and hasAnyRole('MENTORS', 'FACULTY')")
    public ApiResponse<BulkResult> saveBulk(@Valid @RequestBody BulkRequest request) {
        BulkResult result = attendanceService.saveBulk(request);
        return ApiResponse.ok(result, "Attendance saved for " + result.saved() + " students");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_UPDATE') and hasAnyRole('MENTORS', 'FACULTY')")
    public ApiResponse<Record> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(attendanceService.update(id, request), "Attendance updated");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ApiResponse<PageResponse<Record>> history(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(attendanceService.history(studentId, batchId, status, from, to,
                PageRequest.of(page, Math.min(size, 200))));
    }
}
