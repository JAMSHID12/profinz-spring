package com.coyotai.education.security;

import com.coyotai.education.common.ForbiddenException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Whose data the current user may reach. Permissions answer "may I record attendance?";
 * the scope answers "for which batches?".
 *
 * <ul>
 *   <li>global roles (administrative, academics, directors, accounts, sales): everything</li>
 *   <li>mentors: the batches they mentor and those batches' students</li>
 *   <li>faculty: the batch/subject pairs they are assigned to or scheduled for</li>
 *   <li>students: only their own record, through the /me endpoints</li>
 * </ul>
 */
public final class DataScope {

    /** Never a real id; keeps JPQL "in (...)" valid when a scope is empty. */
    private static final List<Long> EMPTY_SENTINEL = List.of(-1L);

    private final boolean global;
    private final Set<Long> mentorBatchIds;
    private final Set<Long> facultyBatchIds;
    private final Set<String> facultyBatchSubjects;
    private final Long studentId;

    public DataScope(boolean global, Set<Long> mentorBatchIds, Set<Long> facultyBatchIds,
                     Set<String> facultyBatchSubjects, Long studentId) {
        this.global = global;
        this.mentorBatchIds = Set.copyOf(mentorBatchIds);
        this.facultyBatchIds = Set.copyOf(facultyBatchIds);
        this.facultyBatchSubjects = Set.copyOf(facultyBatchSubjects);
        this.studentId = studentId;
    }

    public static DataScope global() {
        return new DataScope(true, Set.of(), Set.of(), Set.of(), null);
    }

    public static String pair(Long batchId, Long subjectId) {
        return batchId + ":" + subjectId;
    }

    public boolean isGlobal() {
        return global;
    }

    public Long studentId() {
        return studentId;
    }

    /** Batches reachable by the user; meaningless when {@link #isGlobal()}. */
    public Set<Long> batchIds() {
        if (facultyBatchIds.isEmpty()) {
            return mentorBatchIds;
        }
        if (mentorBatchIds.isEmpty()) {
            return facultyBatchIds;
        }
        java.util.HashSet<Long> union = new java.util.HashSet<>(mentorBatchIds);
        union.addAll(facultyBatchIds);
        return union;
    }

    public Set<Long> mentorBatchIds() {
        return mentorBatchIds;
    }

    public Set<Long> facultyBatchIds() {
        return facultyBatchIds;
    }

    /** Batch ids for a repository "in" filter, never empty. Use together with {@link #isGlobal()}. */
    public Collection<Long> batchIdsForQuery() {
        Set<Long> ids = batchIds();
        return ids.isEmpty() ? EMPTY_SENTINEL : ids;
    }

    public boolean canAccessBatch(Long batchId) {
        return global || (batchId != null && batchIds().contains(batchId));
    }

    /**
     * Subject-level check used for marks entry and syllabus updates: mentors reach every
     * subject of their batches, faculty only the subjects they teach there.
     */
    public boolean canAccessBatchSubject(Long batchId, Long subjectId) {
        if (global || mentorBatchIds.contains(batchId)) {
            return true;
        }
        return facultyBatchSubjects.contains(pair(batchId, subjectId));
    }

    public void requireBatch(Long batchId) {
        if (!canAccessBatch(batchId)) {
            throw ForbiddenException.outOfScope("batch");
        }
    }

    public void requireBatchSubject(Long batchId, Long subjectId) {
        if (!canAccessBatchSubject(batchId, subjectId)) {
            throw ForbiddenException.outOfScope("batch and subject");
        }
    }
}
