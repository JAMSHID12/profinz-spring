package com.coyotai.education.auth;

import com.coyotai.education.platform.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);

    @Query("select distinct r from Role r left join fetch r.permissions order by r.displayOrder")
    List<Role> findAllWithPermissions();
}
