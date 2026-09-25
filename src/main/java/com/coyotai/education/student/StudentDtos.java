package com.coyotai.education.student;

import com.coyotai.education.common.Ref;
import com.coyotai.education.student.ParentContact;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Request and response shapes for student management. */
public final class StudentDtos {

    private StudentDtos() {
    }

    public record StudentRequest(
            @NotBlank(message = "Student name is required") @Size(max = 150) String fullName,
            LocalDate dateOfBirth,
            @Size(max = 20) String gender,
            @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Enter a valid mobile number") String mobile,
            @Email(message = "Enter a valid e-mail address") @Size(max = 150) String email,
            @Size(max = 500) String address,
            @NotBlank(message = "Parent name is required") @Size(max = 150) String parentName,
            @NotBlank(message = "Parent phone number is required")
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Include country code and 7-15 digits") String parentPhoneNumber,
            Boolean parentWhatsappOptIn,
            Long batchId,
            Long courseId,
            LocalDate admissionDate,
            Student.Status status,
            /* Create a portal login (username = admission number) when saving a new student. */
            Boolean createLogin, Student.EducationCategory educationCategory, Long educationCategoryId
    ) {
    }

    public record TransferRequest(
            @NotNull(message = "The new batch is required") Long batchId,
            LocalDate effectiveDate,
            @Size(max = 255) String reason
    ) {
    }

    public record LoginRequest(
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password
    ) {
    }

    public record ParentSummary(String name, String relation, String phoneNumber, String whatsappNumber,
                                String email, boolean whatsappOptIn) {

        public static ParentSummary from(ParentContact parent) {
            return parent == null ? null : new ParentSummary(parent.getName(), parent.getRelation(),
                    parent.getPhoneNumber(), parent.resolveWhatsappNumber(), parent.getEmail(),
                    parent.isWhatsappOptIn());
        }
    }

    public record StudentResponse(Long id, String studentCode, String admissionNumber, String fullName,
                                  String mobile, String email, Ref course, Ref batch, ParentSummary parent,
                                  LocalDate admissionDate, Student.Status status, String username, Student.EducationCategory educationCategory, EducationCategoryService.Response educationCategoryDetail) {

        public static StudentResponse from(Student student) {
            return new StudentResponse(student.getId(), student.getStudentCode(), student.getAdmissionNumber(),
                    student.getFullName(), student.getMobile(), student.getEmail(),
                    student.getCourse() == null ? null : Ref.of(student.getCourse().getId(), student.getCourse().getName()),
                    student.getBatch() == null ? null : Ref.of(student.getBatch().getId(), student.getBatch().getName()),
                    ParentSummary.from(student.getParent()),
                    student.getAdmissionDate(), student.getStatus(),
                    student.getUser() == null ? null : student.getUser().getUsername(), student.getEducationCategory(), EducationCategoryService.Response.from(student.getEducationCategoryMaster()));
        }
    }

    public record StudentDetail(Long id, String studentCode, String admissionNumber, String fullName,
                                LocalDate dateOfBirth, String gender, String mobile, String email, String address,
                                String photoUrl, Ref course, Ref batch, Ref academicYear, Ref mentor,
                                ParentSummary parent, LocalDate admissionDate, Student.Status status, String username, Student.EducationCategory educationCategory, EducationCategoryService.Response educationCategoryDetail) {

        public static StudentDetail from(Student student) {
            var batch = student.getBatch();
            var mentor = batch == null ? null : batch.getMentor();
            return new StudentDetail(student.getId(), student.getStudentCode(), student.getAdmissionNumber(),
                    student.getFullName(), student.getDateOfBirth(), student.getGender(), student.getMobile(),
                    student.getEmail(), student.getAddress(), student.getPhotoUrl(),
                    student.getCourse() == null ? null : Ref.of(student.getCourse().getId(), student.getCourse().getName()),
                    batch == null ? null : Ref.of(batch.getId(), batch.getName()),
                    student.getAcademicYear() == null ? null
                            : Ref.of(student.getAcademicYear().getId(), student.getAcademicYear().getName()),
                    mentor == null ? null : Ref.of(mentor.getId(), mentor.getFullName()),
                    ParentSummary.from(student.getParent()),
                    student.getAdmissionDate(), student.getStatus(),
                    student.getUser() == null ? null : student.getUser().getUsername(), student.getEducationCategory(), EducationCategoryService.Response.from(student.getEducationCategoryMaster()));
        }
    }

    public record BatchHistoryEntry(Long id, Ref batch, Ref course, Ref academicYear, LocalDate startDate,
                                    LocalDate endDate, StudentBatchAssignment.Status status, String reason) {

        static BatchHistoryEntry from(StudentBatchAssignment assignment) {
            return new BatchHistoryEntry(assignment.getId(),
                    Ref.of(assignment.getBatch().getId(), assignment.getBatch().getName()),
                    Ref.of(assignment.getCourse().getId(), assignment.getCourse().getName()),
                    Ref.of(assignment.getAcademicYear().getId(), assignment.getAcademicYear().getName()),
                    assignment.getStartDate(), assignment.getEndDate(), assignment.getStatus(), assignment.getReason());
        }
    }
}
