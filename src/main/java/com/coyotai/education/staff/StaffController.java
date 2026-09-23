package com.coyotai.education.staff;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.staff.StaffDtos.AssignmentRequest;
import com.coyotai.education.staff.StaffDtos.AssignmentResponse;
import com.coyotai.education.staff.StaffDtos.FacultyResponse;
import com.coyotai.education.staff.StaffDtos.MentorResponse;
import com.coyotai.education.staff.StaffDtos.StaffRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ACADEMICS)
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/mentors")
    @PreAuthorize("hasAuthority('MENTOR_VIEW')")
    public ApiResponse<List<MentorResponse>> mentors() {
        return ApiResponse.ok(staffService.mentors());
    }

    @PostMapping("/mentors")
    @PreAuthorize("hasAuthority('MENTOR_MANAGE')")
    public ApiResponse<MentorResponse> createMentor(@Valid @RequestBody StaffRequest request) {
        return ApiResponse.ok(staffService.createMentor(request), "Mentor created");
    }

    @PutMapping("/mentors/{id}")
    @PreAuthorize("hasAuthority('MENTOR_MANAGE')")
    public ApiResponse<MentorResponse> updateMentor(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return ApiResponse.ok(staffService.updateMentor(id, request), "Mentor updated");
    }

    @GetMapping("/faculty")
    @PreAuthorize("hasAuthority('FACULTY_VIEW')")
    public ApiResponse<List<FacultyResponse>> faculty() {
        return ApiResponse.ok(staffService.faculty());
    }

    @PostMapping("/faculty")
    @PreAuthorize("hasAuthority('FACULTY_MANAGE')")
    public ApiResponse<FacultyResponse> createFaculty(@Valid @RequestBody StaffRequest request) {
        return ApiResponse.ok(staffService.createFaculty(request), "Faculty created");
    }

    @PutMapping("/faculty/{id}")
    @PreAuthorize("hasAuthority('FACULTY_MANAGE')")
    public ApiResponse<FacultyResponse> updateFaculty(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return ApiResponse.ok(staffService.updateFaculty(id, request), "Faculty updated");
    }

    @GetMapping("/faculty-assignments")
    @PreAuthorize("hasAuthority('FACULTY_VIEW')")
    public ApiResponse<List<AssignmentResponse>> assignments(@RequestParam(required = false) Long facultyId,
                                                             @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(staffService.assignments(facultyId, batchId));
    }

    @PostMapping("/faculty-assignments")
    @PreAuthorize("hasAuthority('FACULTY_MANAGE')")
    public ApiResponse<AssignmentResponse> assign(@Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.ok(staffService.assign(request), "Faculty assigned");
    }

    @PutMapping("/faculty-assignments/{id}/active")
    @PreAuthorize("hasAuthority('FACULTY_MANAGE')")
    public ApiResponse<AssignmentResponse> setActive(@PathVariable Long id, @RequestParam boolean value) {
        return ApiResponse.ok(staffService.setAssignmentActive(id, value));
    }
}
