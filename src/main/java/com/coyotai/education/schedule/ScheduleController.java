package com.coyotai.education.schedule;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.schedule.ScheduleDtos.Conflict;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitRequest;
import com.coyotai.education.schedule.ScheduleDtos.EntryExitResponse;
import com.coyotai.education.schedule.ScheduleDtos.RegisterRequest;
import com.coyotai.education.schedule.ScheduleDtos.RegisterResponse;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleRequest;
import com.coyotai.education.schedule.ScheduleDtos.ScheduleResponse;
import jakarta.validation.Valid;
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

/** Class schedule, class register and faculty entry/exit. */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ACADEMICS)
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final TeachingService teachingService;
    private final ProjectConfigService configService;

    public ScheduleController(ScheduleService scheduleService, TeachingService teachingService,
                              ProjectConfigService configService) {
        this.scheduleService = scheduleService;
        this.teachingService = teachingService;
        this.configService = configService;
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('SCHEDULE_VIEW', 'MY_SCHEDULE_VIEW')")
    public ApiResponse<List<ScheduleResponse>> schedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(defaultValue = "false") boolean mine) {
        LocalDate start = from == null ? configService.today() : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        return ApiResponse.ok(scheduleService.search(start, end, batchId, facultyId, mine));
    }

    @PostMapping("/schedules/conflicts")
    @PreAuthorize("hasAnyAuthority('SCHEDULE_CREATE', 'SCHEDULE_UPDATE')")
    public ApiResponse<List<Conflict>> conflicts(@Valid @RequestBody ScheduleRequest request,
                                                 @RequestParam(required = false) Long excludeId) {
        return ApiResponse.ok(scheduleService.checkConflicts(request, excludeId));
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('SCHEDULE_CREATE')")
    public ApiResponse<List<ScheduleResponse>> create(@Valid @RequestBody ScheduleRequest request) {
        List<ScheduleResponse> created = scheduleService.create(request);
        return ApiResponse.ok(created, created.size() == 1 ? "Class scheduled" : created.size() + " classes scheduled");
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_UPDATE')")
    public ApiResponse<ScheduleResponse> update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(scheduleService.update(id, request), "Class updated");
    }

    @GetMapping("/class-register")
    @PreAuthorize("hasAuthority('CLASS_REGISTER_VIEW')")
    public ApiResponse<List<RegisterResponse>> register(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return ApiResponse.ok(teachingService.register(start, end, mine));
    }

    @PostMapping("/class-register")
    @PreAuthorize("hasAuthority('CLASS_REGISTER_CREATE')")
    public ApiResponse<RegisterResponse> recordRegister(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(teachingService.record(request), "Class register saved");
    }

    @GetMapping("/faculty-entry-exit")
    @PreAuthorize("hasAuthority('FACULTY_ENTRY_EXIT_VIEW')")
    public ApiResponse<List<EntryExitResponse>> entries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long facultyId) {
        LocalDate end = to == null ? configService.today() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        return ApiResponse.ok(teachingService.entries(start, end, facultyId));
    }

    @PostMapping("/faculty-entry-exit")
    @PreAuthorize("hasAuthority('FACULTY_ENTRY_EXIT_CREATE')")
    public ApiResponse<EntryExitResponse> recordEntry(@Valid @RequestBody EntryExitRequest request) {
        return ApiResponse.ok(teachingService.recordEntry(request), "Entry recorded");
    }

    @PutMapping("/faculty-entry-exit/{id}")
    @PreAuthorize("hasAuthority('FACULTY_ENTRY_EXIT_CREATE')")
    public ApiResponse<EntryExitResponse> updateEntry(@PathVariable Long id, @Valid @RequestBody EntryExitRequest request) {
        return ApiResponse.ok(teachingService.updateEntry(id, request), "Entry updated");
    }
}
