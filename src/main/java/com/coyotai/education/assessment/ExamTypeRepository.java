package com.coyotai.education.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {

    List<ExamType> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    long count();
}
