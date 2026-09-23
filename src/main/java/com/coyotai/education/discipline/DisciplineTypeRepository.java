package com.coyotai.education.discipline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisciplineTypeRepository extends JpaRepository<DisciplineType, Long> {

    List<DisciplineType> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    Optional<DisciplineType> findFirstByCodeIgnoreCaseAndActiveTrue(String code);
}
