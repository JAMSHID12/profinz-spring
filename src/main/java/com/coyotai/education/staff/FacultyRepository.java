package com.coyotai.education.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    @Query("select f from Faculty f left join fetch f.user order by f.fullName")
    List<Faculty> findAllWithUser();

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    long countByActiveTrue();
}
