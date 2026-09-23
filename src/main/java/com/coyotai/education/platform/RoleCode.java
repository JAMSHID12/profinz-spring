package com.coyotai.education.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every role the platform understands. Whether a role is usable is decided per client by
 * {@code project.role.<name>.enabled}; what it may do is decided by role permissions.
 */
public enum RoleCode {

    DIRECTORS(Scope.GLOBAL),
    ADMINISTRATIVE(Scope.GLOBAL),
    SALES(Scope.GLOBAL),
    ACCOUNTS(Scope.GLOBAL),
    ACADEMICS(Scope.GLOBAL),
    STUDENTS(Scope.STUDENT),
    MENTORS(Scope.MENTOR),
    FACULTY(Scope.FACULTY);

    /**
     * The only roles that take or correct attendance: a mentor for the batches they mentor,
     * faculty for the classes they teach. Enforced in code whatever the role permissions say.
     */
    public static final Set<RoleCode> ATTENDANCE_TAKERS = Collections.unmodifiableSet(EnumSet.of(MENTORS, FACULTY));

    /** How far a role's data access reaches. Permissions say what, scope says whose. */
    public enum Scope {
        /** The whole centre. */
        GLOBAL,
        /** Batches the user mentors, and their students. */
        MENTOR,
        /** Batches and subjects the user teaches. */
        FACULTY,
        /** The user's own student record only. */
        STUDENT
    }

    private final Scope scope;

    RoleCode(Scope scope) {
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }
}
