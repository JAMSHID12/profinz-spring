package com.coyotai.education.academic;

import com.coyotai.education.academic.AcademicDtos.AcademicYearRequest;
import com.coyotai.education.academic.AcademicDtos.AcademicYearResponse;
import com.coyotai.education.academic.AcademicDtos.BatchRequest;
import com.coyotai.education.academic.AcademicDtos.BatchResponse;
import com.coyotai.education.academic.AcademicDtos.CourseRequest;
import com.coyotai.education.academic.AcademicDtos.CourseResponse;
import com.coyotai.education.academic.AcademicDtos.SubjectRequest;
import com.coyotai.education.academic.AcademicDtos.SubjectResponse;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
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

/** Courses, subjects, academic years and batches. */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ACADEMICS)
public class AcademicController {

    private final CourseService courseService;
    private final AcademicYearService academicYearService;
    private final BatchService batchService;

    public AcademicController(CourseService courseService, AcademicYearService academicYearService,
                              BatchService batchService) {
        this.courseService = courseService;
        this.academicYearService = academicYearService;
        this.batchService = batchService;
    }

    // ---- Courses ------------------------------------------------------------

    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('COURSE_VIEW')")
    public ApiResponse<List<CourseResponse>> courses(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(courseService.courses(activeOnly));
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('COURSE_CREATE')")
    public ApiResponse<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
        return ApiResponse.ok(courseService.createCourse(request), "Course created");
    }

    @PutMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('COURSE_UPDATE')")
    public ApiResponse<CourseResponse> updateCourse(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return ApiResponse.ok(courseService.updateCourse(id, request), "Course updated");
    }

    // ---- Subjects -----------------------------------------------------------

    @GetMapping("/subjects")
    @PreAuthorize("hasAuthority('SUBJECT_VIEW')")
    public ApiResponse<List<SubjectResponse>> subjects(@RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(courseService.subjects(courseId));
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAuthority('SUBJECT_CREATE')")
    public ApiResponse<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
        return ApiResponse.ok(courseService.createSubject(request), "Subject created");
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('SUBJECT_UPDATE')")
    public ApiResponse<SubjectResponse> updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.ok(courseService.updateSubject(id, request), "Subject updated");
    }

    // ---- Academic years -----------------------------------------------------

    @GetMapping("/academic-years")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_VIEW')")
    public ApiResponse<List<AcademicYearResponse>> academicYears() {
        return ApiResponse.ok(academicYearService.findAll());
    }

    @PostMapping("/academic-years")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_MANAGE')")
    public ApiResponse<AcademicYearResponse> createAcademicYear(@Valid @RequestBody AcademicYearRequest request) {
        return ApiResponse.ok(academicYearService.create(request), "Academic year created");
    }

    @PutMapping("/academic-years/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_MANAGE')")
    public ApiResponse<AcademicYearResponse> updateAcademicYear(@PathVariable Long id,
                                                                @Valid @RequestBody AcademicYearRequest request) {
        return ApiResponse.ok(academicYearService.update(id, request), "Academic year updated");
    }

    // ---- Batches ------------------------------------------------------------

    @GetMapping("/batches")
    @PreAuthorize("hasAnyAuthority('BATCH_VIEW', 'MY_BATCH_VIEW')")
    public ApiResponse<List<BatchResponse>> batches(@RequestParam(required = false) Long courseId,
                                                    @RequestParam(required = false) Long academicYearId,
                                                    @RequestParam(required = false) Batch.Status status) {
        return ApiResponse.ok(batchService.search(courseId, academicYearId, status));
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyAuthority('BATCH_VIEW', 'MY_BATCH_VIEW')")
    public ApiResponse<BatchResponse> batch(@PathVariable Long id) {
        return ApiResponse.ok(batchService.findById(id));
    }

    @PostMapping("/batches")
    @PreAuthorize("hasAuthority('BATCH_CREATE')")
    public ApiResponse<BatchResponse> createBatch(@Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(batchService.create(request), "Batch created");
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize("hasAuthority('BATCH_UPDATE')")
    public ApiResponse<BatchResponse> updateBatch(@PathVariable Long id, @Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(batchService.update(id, request), "Batch updated");
    }
}
