package com.coyotai.education.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query("""
            select s from Student s
            left join fetch s.course
            left join fetch s.batch b
            where (:search is null or lower(s.fullName) like lower(concat('%', :search, '%'))
                   or lower(s.admissionNumber) like lower(concat('%', :search, '%'))
                   or lower(s.studentCode) like lower(concat('%', :search, '%')))
              and (:courseId is null or s.course.id = :courseId)
              and (:batchId is null or b.id = :batchId)
              and (:status is null or s.status = :status)
              and (:allBatches = true or b.id in :batchIds)
            order by s.fullName
            """)
    List<Student> search(@Param("search") String search,
                         @Param("courseId") Long courseId,
                         @Param("batchId") Long batchId,
                         @Param("status") Student.Status status,
                         @Param("allBatches") boolean allBatches,
                         @Param("batchIds") Collection<Long> batchIds);

    @Query("""
            select s from Student s
            left join fetch s.course
            left join fetch s.batch b
            left join fetch b.mentor
            left join fetch s.academicYear
            left join fetch s.user
            where s.id = :id
            """)
    Optional<Student> findDetail(@Param("id") Long id);

    @Query("""
            select s from Student s where s.batch.id = :batchId and s.status = com.coyotai.education.student.Student.Status.ACTIVE
            order by s.fullName
            """)
    List<Student> findActiveByBatchId(@Param("batchId") Long batchId);

    @Query("""
            select s.batch.id, count(s) from Student s
            where s.status = com.coyotai.education.student.Student.Status.ACTIVE and s.batch is not null
            group by s.batch.id
            """)
    List<Object[]> countActiveByBatch();

    @Query("""
            select count(s) from Student s
            where s.status = com.coyotai.education.student.Student.Status.ACTIVE
              and (:allBatches = true or s.batch.id in :batchIds)
            """)
    long countActive(@Param("allBatches") boolean allBatches, @Param("batchIds") Collection<Long> batchIds);

    @Query("select s.id from Student s where s.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    boolean existsByParentPhoneNumber(String phoneNumber);

    boolean existsByAdmissionNumberIgnoreCase(String admissionNumber);

    boolean existsByStudentCodeIgnoreCase(String studentCode);
}
