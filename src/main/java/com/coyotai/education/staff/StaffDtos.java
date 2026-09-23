package com.coyotai.education.staff;

import com.coyotai.education.common.Ref;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for mentors, faculty and teaching assignments. */
public final class StaffDtos {

    private StaffDtos() {
    }

    /** Username/password are required when creating; ignored on update (use password reset). */
    public record StaffRequest(
            @NotBlank(message = "Full name is required") @Size(max = 150) String fullName,
            @Size(max = 30) String employeeCode,
            @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Enter a valid mobile number") String mobile,
            @Email(message = "Enter a valid e-mail address") @Size(max = 150) String email,
            @Size(max = 150) String specialization,
            Faculty.Type facultyType,
            Boolean active,
            @Size(min = 3, max = 60, message = "Username must be 3-60 characters")
            @Pattern(regexp = "^$|^[A-Za-z0-9._@-]+$", message = "Username may contain letters, digits and . _ @ - only")
            String username,
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password
    ) {
    }

    public record MentorResponse(Long id, String fullName, String employeeCode, String mobile, String email,
                                 String specialization, boolean active, String username, long batchCount) {

        static MentorResponse from(Mentor mentor, long batchCount) {
            return new MentorResponse(mentor.getId(), mentor.getFullName(), mentor.getEmployeeCode(),
                    mentor.getMobile(), mentor.getEmail(), mentor.getSpecialization(), mentor.isActive(),
                    mentor.getUser() == null ? null : mentor.getUser().getUsername(), batchCount);
        }
    }

    public record FacultyResponse(Long id, String fullName, String employeeCode, String mobile, String email,
                                  Faculty.Type facultyType, String specialization, boolean active, String username) {

        static FacultyResponse from(Faculty faculty) {
            return new FacultyResponse(faculty.getId(), faculty.getFullName(), faculty.getEmployeeCode(),
                    faculty.getMobile(), faculty.getEmail(), faculty.getFacultyType(), faculty.getSpecialization(),
                    faculty.isActive(), faculty.getUser() == null ? null : faculty.getUser().getUsername());
        }
    }

    public record AssignmentRequest(
            @NotNull(message = "Faculty is required") Long facultyId,
            @NotNull(message = "Batch is required") Long batchId,
            @NotNull(message = "Subject is required") Long subjectId
    ) {
    }

    public record AssignmentResponse(Long id, Ref faculty, Ref batch, Ref subject, boolean active) {

        static AssignmentResponse from(FacultyAssignment assignment) {
            return new AssignmentResponse(assignment.getId(),
                    Ref.of(assignment.getFaculty().getId(), assignment.getFaculty().getFullName()),
                    Ref.of(assignment.getBatch().getId(), assignment.getBatch().getName()),
                    Ref.of(assignment.getSubject().getId(), assignment.getSubject().getName()),
                    assignment.isActive());
        }
    }
}
