package com.coyotai.education.discipline;

import com.coyotai.education.assessment.AssessmentDtos.ExamTypeRequest;
import com.coyotai.education.assessment.AssessmentDtos.ExamTypeResponse;
import com.coyotai.education.assessment.ExamTypeService;
import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.discipline.DisciplineDtos.FineRequest;
import com.coyotai.education.discipline.DisciplineDtos.FineResponse;
import com.coyotai.education.discipline.DisciplineDtos.FineStatusRequest;
import com.coyotai.education.discipline.DisciplineDtos.RecordRequest;
import com.coyotai.education.discipline.DisciplineDtos.RecordResponse;
import com.coyotai.education.discipline.DisciplineDtos.TypeRequest;
import com.coyotai.education.discipline.DisciplineDtos.TypeResponse;
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

/** Discipline, fines and the per-client master lists (discipline types, exam types). */
@RestController
@RequestMapping("/api")
@RequiresModule(ModuleCode.ACADEMICS)
public class DisciplineController {

    private final DisciplineService disciplineService;
    private final ExamTypeService examTypeService;

    public DisciplineController(DisciplineService disciplineService, ExamTypeService examTypeService) {
        this.disciplineService = disciplineService;
        this.examTypeService = examTypeService;
    }

    @GetMapping("/discipline-records")
    @PreAuthorize("hasAuthority('DISCIPLINE_VIEW')")
    public ApiResponse<List<RecordResponse>> records(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(disciplineService.records(studentId, batchId, from, to));
    }

    @PostMapping("/discipline-records")
    @PreAuthorize("hasAuthority('DISCIPLINE_CREATE')")
    public ApiResponse<RecordResponse> createRecord(@Valid @RequestBody RecordRequest request) {
        return ApiResponse.ok(disciplineService.createRecord(request), "Discipline record saved");
    }

    @PutMapping("/discipline-records/{id}")
    @PreAuthorize("hasAuthority('DISCIPLINE_UPDATE')")
    public ApiResponse<RecordResponse> updateRecord(@PathVariable Long id, @Valid @RequestBody RecordRequest request) {
        return ApiResponse.ok(disciplineService.updateRecord(id, request), "Discipline record updated");
    }

    @GetMapping("/fines")
    @PreAuthorize("hasAuthority('FINE_VIEW')")
    public ApiResponse<List<FineResponse>> fines(@RequestParam(required = false) Long studentId,
                                                 @RequestParam(required = false) StudentFine.Status status,
                                                 @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(disciplineService.fines(studentId, status, batchId));
    }

    @PostMapping("/fines")
    @PreAuthorize("hasAuthority('FINE_CREATE')")
    public ApiResponse<FineResponse> createFine(@Valid @RequestBody FineRequest request) {
        return ApiResponse.ok(disciplineService.createFine(request), "Fine created");
    }

    @PostMapping("/fines/{id}/status")
    @PreAuthorize("hasAuthority('FINE_UPDATE')")
    public ApiResponse<FineResponse> fineStatus(@PathVariable Long id, @Valid @RequestBody FineStatusRequest request) {
        return ApiResponse.ok(disciplineService.changeFineStatus(id, request), "Fine marked " + request.status());
    }

    @GetMapping("/discipline-types")
    @PreAuthorize("hasAnyAuthority('DISCIPLINE_VIEW', 'MASTER_DATA_MANAGE')")
    public ApiResponse<List<TypeResponse>> disciplineTypes() {
        return ApiResponse.ok(disciplineService.types());
    }

    @PostMapping("/discipline-types")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<TypeResponse> createDisciplineType(@Valid @RequestBody TypeRequest request) {
        return ApiResponse.ok(disciplineService.saveType(null, request), "Discipline type created");
    }

    @PutMapping("/discipline-types/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<TypeResponse> updateDisciplineType(@PathVariable Long id, @Valid @RequestBody TypeRequest request) {
        return ApiResponse.ok(disciplineService.saveType(id, request), "Discipline type updated");
    }

    @GetMapping("/exam-types")
    @PreAuthorize("hasAnyAuthority('EXAM_VIEW', 'MASTER_DATA_MANAGE')")
    public ApiResponse<List<ExamTypeResponse>> examTypes() {
        return ApiResponse.ok(examTypeService.types());
    }

    @PostMapping("/exam-types")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<ExamTypeResponse> createExamType(@Valid @RequestBody ExamTypeRequest request) {
        return ApiResponse.ok(examTypeService.save(null, request), "Exam type created");
    }

    @PutMapping("/exam-types/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_MANAGE')")
    public ApiResponse<ExamTypeResponse> updateExamType(@PathVariable Long id, @Valid @RequestBody ExamTypeRequest request) {
        return ApiResponse.ok(examTypeService.save(id, request), "Exam type updated");
    }
}
