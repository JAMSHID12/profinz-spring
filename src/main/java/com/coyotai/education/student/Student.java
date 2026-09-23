package com.coyotai.education.student;

import com.coyotai.education.academic.AcademicYear;
import com.coyotai.education.academic.Batch;
import com.coyotai.education.academic.Course;
import com.coyotai.education.auth.User;
import com.coyotai.education.common.AuditedEntity;
import com.coyotai.education.student.ParentContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Student profile. The batch/course/year columns are the <em>current</em> placement; the full
 * history lives in {@link StudentBatchAssignment}.
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student extends AuditedEntity {

    public enum Status { ACTIVE, INACTIVE, COMPLETED, DROPPED, SUSPENDED }

    /** Login account, when the student has portal access. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "student_code", nullable = false, length = 30)
    private String studentCode;

    @Column(name = "admission_number", nullable = false, length = 40)
    private String admissionNumber;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 20)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Column(length = 500)
    private String address;

    /** Optional picture shown on the attendance sheet (an image URL). */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "photo_file", length = 100)
    private String photoFile;

    @jakarta.persistence.Embedded
    private ParentContact parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id")
    private AcademicYear academicYear;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
