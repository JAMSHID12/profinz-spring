package com.coyotai.education.staff;

import com.coyotai.education.auth.User;
import com.coyotai.education.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Teaching staff, including guest and visiting teachers. */
@Entity
@Table(name = "faculty")
@Getter
@Setter
@NoArgsConstructor
public class Faculty extends AuditedEntity {

    public enum Type { FULL_TIME, GUEST, VISITING }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "employee_code", length = 30)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "faculty_type", nullable = false, length = 20)
    private Type facultyType = Type.FULL_TIME;

    @Column(length = 150)
    private String specialization;

    @Column(nullable = false)
    private boolean active = true;
}
