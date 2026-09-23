package com.coyotai.education.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MentorRepository extends JpaRepository<Mentor, Long> {

    @Query("select m from Mentor m left join fetch m.user order by m.fullName")
    List<Mentor> findAllWithUser();

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    long countByActiveTrue();
}
