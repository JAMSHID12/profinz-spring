package com.coyotai.education.auth;

import com.coyotai.education.platform.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
            select distinct u from User u
            left join fetch u.roles r
            left join fetch r.permissions
            where lower(u.username) = lower(:username)
            """)
    Optional<User> findWithAuthoritiesByUsername(@Param("username") String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("select distinct u from User u left join fetch u.roles order by u.fullName")
    List<User> findAllWithRoles();

    @Query("""
            select distinct u from User u left join fetch u.roles
            where (:search is null or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(u.username) like lower(concat('%', :search, '%')))
            order by u.fullName
            """)
    List<User> search(@Param("search") String search);

    @Query("select count(distinct u) from User u join u.roles r where r.code = :role and u.active = true")
    long countActiveWithRole(@Param("role") RoleCode role);
}
