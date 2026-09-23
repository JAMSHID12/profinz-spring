package com.coyotai.education.parentmeeting;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.parentmeeting.ParentMeetingService.MeetingRequest;
import com.coyotai.education.parentmeeting.ParentMeetingService.MeetingResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
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

@RestController
@RequestMapping("/api/parent-meetings")
@RequiresModule(ModuleCode.ACADEMICS)
public class ParentMeetingController {

    private final ParentMeetingService service;

    public ParentMeetingController(ParentMeetingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PARENT_MEETING_VIEW')")
    public ApiResponse<List<MeetingResponse>> search(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) ParentMeeting.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.search(studentId, status, from, to));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PARENT_MEETING_CREATE')")
    public ApiResponse<MeetingResponse> create(@Valid @RequestBody MeetingRequest request) {
        return ApiResponse.ok(service.save(null, request), "Parent meeting saved");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARENT_MEETING_CREATE')")
    public ApiResponse<MeetingResponse> update(@PathVariable Long id, @Valid @RequestBody MeetingRequest request) {
        return ApiResponse.ok(service.save(id, request), "Parent meeting updated");
    }
}
