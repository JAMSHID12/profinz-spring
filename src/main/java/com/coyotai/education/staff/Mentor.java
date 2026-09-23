package com.coyotai.education.staff;

import com.coyotai.education.auth.User;
import com.coyotai.education.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mentor profile. Signs in through its linked {@link User}. */
@Entity
@Table(name = "mentors")
@Getter
@Setter
@NoArgsConstructor
public class Mentor extends AuditedEntity {

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

    @Column(length = 150)
    private String specialization;

    @Column(nullable = false)
    private boolean active = true;
}
