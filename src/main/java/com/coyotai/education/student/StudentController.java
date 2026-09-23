package com.coyotai.education.student;

import com.coyotai.education.common.ApiResponse;
import com.coyotai.education.platform.ModuleCode;
import com.coyotai.education.platform.RequiresModule;
import com.coyotai.education.student.StudentDtos.BatchHistoryEntry;
import com.coyotai.education.student.StudentDtos.LoginRequest;
import com.coyotai.education.student.StudentDtos.StudentDetail;
import com.coyotai.education.student.StudentDtos.StudentRequest;
import com.coyotai.education.student.StudentDtos.StudentResponse;
import com.coyotai.education.student.StudentDtos.TransferRequest;
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

/** Student management for staff. Students use /api/students/me/** instead. */
@RestController
@RequestMapping("/api/students")
@RequiresModule(ModuleCode.ACADEMICS)
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW', 'ASSIGNED_STUDENT_VIEW')")
    public ApiResponse<List<StudentResponse>> search(@RequestParam(required = false) String search,
                                                     @RequestParam(required = false) Long courseId,
                                                     @RequestParam(required = false) Long batchId,
                                                     @RequestParam(required = false) Student.Status status) {
        return ApiResponse.ok(studentService.search(search, courseId, batchId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW', 'ASSIGNED_STUDENT_VIEW')")
    public ApiResponse<StudentDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(studentService.detail(id));
    }

    @GetMapping("/{id}/batch-history")
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW', 'ASSIGNED_STUDENT_VIEW')")
    public ApiResponse<List<BatchHistoryEntry>> batchHistory(@PathVariable Long id) {
        return ApiResponse.ok(studentService.batchHistory(id));
    }

    @PostMapping(consumes = "application/json")
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public ApiResponse<StudentDetail> create(@Valid @RequestBody StudentRequest request) {
        return ApiResponse.ok(studentService.create(request), "Student created");
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public ApiResponse<StudentDetail> update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return ApiResponse.ok(studentService.update(id, request), "Student updated");
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('STUDENT_CREATE')")
    public ApiResponse<StudentDetail> createWithPhoto(
            @Valid @org.springframework.web.bind.annotation.RequestPart("student") StudentRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo) {
        return ApiResponse.ok(studentService.saveWithPhoto(null, request, photo), "Student created");
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public ApiResponse<StudentDetail> updateWithPhoto(@PathVariable Long id,
            @Valid @org.springframework.web.bind.annotation.RequestPart("student") StudentRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo) {
        return ApiResponse.ok(studentService.saveWithPhoto(id, request, photo), "Student updated");
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAnyAuthority('STUDENT_VIEW', 'ASSIGNED_STUDENT_VIEW')")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> photo(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_JPEG)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(studentService.photo(id));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public ApiResponse<StudentDetail> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return ApiResponse.ok(studentService.transfer(id, request), "Student moved to the new batch");
    }

    @PostMapping("/{id}/login")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public ApiResponse<StudentDetail> login(@PathVariable Long id, @Valid @RequestBody(required = false) LoginRequest request) {
        return ApiResponse.ok(studentService.createOrResetLogin(id, request == null ? null : request.password()),
                "Student login ready. The student must change the password at first sign-in.");
    }
}
