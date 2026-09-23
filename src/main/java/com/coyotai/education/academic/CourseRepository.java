package com.coyotai.education.academic;

import com.coyotai.education.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByOrderByDisplayOrderAscNameAsc();

    List<Course> findAllByStatusOrderByDisplayOrderAscNameAsc(RecordStatus status);

    boolean existsByCodeIgnoreCase(String code);

    long countByStatus(RecordStatus status);
}
