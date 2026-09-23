package com.coyotai.education.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single capability, e.g. ATTENDANCE_CREATE. Platform-wide reference data. */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** Grouping for the roles screen: PLATFORM, ACADEMICS, FEES, ... */
    @Column(name = "module_code", nullable = false, length = 30)
    private String moduleCode;

    @Column(length = 255)
    private String description;
}
