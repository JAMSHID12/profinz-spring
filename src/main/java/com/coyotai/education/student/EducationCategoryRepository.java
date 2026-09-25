package com.coyotai.education.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationCategoryRepository extends JpaRepository<EducationCategory, Long> {

    List<EducationCategory> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    java.util.Optional<EducationCategory> findByCodeIgnoreCase(String code);
}
