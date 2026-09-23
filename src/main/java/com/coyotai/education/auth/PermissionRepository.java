package com.coyotai.education.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleCodeAscCodeAsc();

    List<Permission> findAllByCodeIn(Collection<String> codes);
}
