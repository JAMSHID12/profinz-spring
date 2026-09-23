package com.coyotai.education.assessment;

import com.coyotai.education.assessment.AssessmentDtos.ExamRequest;
import com.coyotai.education.assessment.AssessmentDtos.ExamResponse;
import com.coyotai.education.assessment.AssessmentDtos.MarksEntryRequest;
import com.coyotai.education.assessment.AssessmentDtos.MarksSheet;
import com.coyotai.education.assessment.AssessmentDtos.StatusRequest;
import com.coyotai.education.assessment.AssessmentDtos.TestRequest;
import com.coyotai.education.assessment.AssessmentDtos.TestResponse;
import com.coyotai.education.common.ApiResponse;
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

/** Daily tests, weekly tests and exams. */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ACADEMICS)
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    // ---- Tests --------------------------------------------------------------

    @GetMapping("/tests")
    @PreAuthorize("hasAuthority('TEST_VIEW')")
    public ApiResponse<List<TestResponse>> tests(
            @RequestParam(required = false) AcademicTest.Type type,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) PublicationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(assessmentService.tests(type, batchId, subjectId, status, from, to));
    }

    @PostMapping("/tests")
    @PreAuthorize("hasAuthority('TEST_CREATE')")
    public ApiResponse<TestResponse> createTest(@Valid @RequestBody TestRequest request) {
        return ApiResponse.ok(assessmentService.createTest(request), "Test created");
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("hasAuthority('TEST_CREATE')")
    public ApiResponse<TestResponse> updateTest(@PathVariable Long id, @Valid @RequestBody TestRequest request) {
        return ApiResponse.ok(assessmentService.updateTest(id, request), "Test updated");
    }

    @GetMapping("/tests/{id}/results")
    @PreAuthorize("hasAnyAuthority('TEST_VIEW', 'TEST_MARKS_ENTRY')")
    public ApiResponse<MarksSheet> testSheet(@PathVariable Long id) {
        return ApiResponse.ok(assessmentService.testSheet(id));
    }

    @PutMapping("/tests/{id}/results")
    @PreAuthorize("hasAuthority('TEST_MARKS_ENTRY')")
    public ApiResponse<MarksSheet> saveTestMarks(@PathVariable Long id, @Valid @RequestBody MarksEntryRequest request) {
        return ApiResponse.ok(assessmentService.saveTestMarks(id, request), "Marks saved");
    }

    @PostMapping("/tests/{id}/status")
    @PreAuthorize("hasAuthority('TEST_PUBLISH')")
    public ApiResponse<TestResponse> testStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(assessmentService.changeTestStatus(id, request.status()), "Test moved to " + request.status());
    }

    // ---- Exams --------------------------------------------------------------

    @GetMapping("/exams")
    @PreAuthorize("hasAuthority('EXAM_VIEW')")
    public ApiResponse<List<ExamResponse>> exams(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) PublicationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(assessmentService.exams(batchId, subjectId, status, from, to));
    }

    @PostMapping("/exams")
    @PreAuthorize("hasAuthority('EXAM_CREATE')")
    public ApiResponse<ExamResponse> createExam(@Valid @RequestBody ExamRequest request) {
        return ApiResponse.ok(assessmentService.createExam(request), "Exam created");
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasAuthority('EXAM_CREATE')")
    public ApiResponse<ExamResponse> updateExam(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return ApiResponse.ok(assessmentService.updateExam(id, request), "Exam updated");
    }

    @GetMapping("/exams/{id}/results")
    @PreAuthorize("hasAnyAuthority('EXAM_VIEW', 'EXAM_MARKS_ENTRY')")
    public ApiResponse<MarksSheet> examSheet(@PathVariable Long id) {
        return ApiResponse.ok(assessmentService.examSheet(id));
    }

    @PutMapping("/exams/{id}/results")
    @PreAuthorize("hasAuthority('EXAM_MARKS_ENTRY')")
    public ApiResponse<MarksSheet> saveExamMarks(@PathVariable Long id, @Valid @RequestBody MarksEntryRequest request) {
        return ApiResponse.ok(assessmentService.saveExamMarks(id, request), "Marks saved");
    }

    @PostMapping("/exams/{id}/status")
    @PreAuthorize("hasAuthority('EXAM_PUBLISH')")
    public ApiResponse<ExamResponse> examStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(assessmentService.changeExamStatus(id, request.status()), "Exam moved to " + request.status());
    }
}
