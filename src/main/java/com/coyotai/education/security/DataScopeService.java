package com.coyotai.education.security;

import com.coyotai.education.academic.BatchRepository;
import com.coyotai.education.common.ForbiddenException;
import com.coyotai.education.platform.RoleCode;
import com.coyotai.education.schedule.ClassScheduleRepository;
import com.coyotai.education.staff.FacultyAssignmentRepository;
import com.coyotai.education.student.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashSet;
import java.util.Set;

/** Computes the current user's {@link DataScope}, once per request. */
@Service
public class DataScopeService {

    private static final String CACHE_KEY = DataScopeService.class.getName() + ".scope";

    private final BatchRepository batchRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final ClassScheduleRepository classScheduleRepository;

    public DataScopeService(BatchRepository batchRepository,
                            FacultyAssignmentRepository facultyAssignmentRepository,
                            ClassScheduleRepository classScheduleRepository) {
        this.batchRepository = batchRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.classScheduleRepository = classScheduleRepository;
    }

    @Transactional(readOnly = true)
    public DataScope current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof DataScope scope) {
                return scope;
            }
        }
        DataScope scope = compute(CurrentUser.require());
        if (attributes != null) {
            attributes.setAttribute(CACHE_KEY, scope, RequestAttributes.SCOPE_REQUEST);
        }
        return scope;
    }

    DataScope compute(AppUserDetails user) {
        boolean global = user.getEnabledRoles().stream()
                .anyMatch(role -> role.scope() == RoleCode.Scope.GLOBAL);
        if (global) {
            return DataScope.global();
        }

        Set<Long> mentorBatches = new HashSet<>();
        if (user.hasRole(RoleCode.MENTORS) && user.getMentorId() != null) {
            mentorBatches.addAll(batchRepository.findIdsByMentorId(user.getMentorId()));
        }

        Set<Long> facultyBatches = new HashSet<>();
        Set<String> facultyPairs = new HashSet<>();
        if (user.hasRole(RoleCode.FACULTY) && user.getFacultyId() != null) {
            for (Object[] row : facultyAssignmentRepository.findActivePairs(user.getFacultyId())) {
                addPair(row, facultyBatches, facultyPairs);
            }
            for (Object[] row : classScheduleRepository.findBatchSubjectPairs(user.getFacultyId())) {
                addPair(row, facultyBatches, facultyPairs);
            }
        }

        Long studentId = user.hasRole(RoleCode.STUDENTS) ? user.getStudentId() : null;
        return new DataScope(false, mentorBatches, facultyBatches, facultyPairs, studentId);
    }

    private void addPair(Object[] row, Set<Long> batches, Set<String> pairs) {
        Long batchId = ((Number) row[0]).longValue();
        Long subjectId = ((Number) row[1]).longValue();
        batches.add(batchId);
        pairs.add(DataScope.pair(batchId, subjectId));
    }

    /** A staff member may see a student when the student's current batch is in scope. */
    public void requireStudent(Student student) {
        DataScope scope = current();
        if (scope.isGlobal()) {
            return;
        }
        Long batchId = student.getBatch() == null ? null : student.getBatch().getId();
        if (!scope.canAccessBatch(batchId)) {
            throw ForbiddenException.outOfScope("student");
        }
    }
}
