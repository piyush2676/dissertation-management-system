package com.dms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Unscoped: only the admin reaches this. */
    @org.springframework.data.jpa.repository.Query("""
           select u from User u
           where lower(u.fullName) like lower(concat('%', :q, '%'))
              or lower(u.email) like lower(concat('%', :q, '%'))
           order by u.fullName
           """)
    java.util.List<User> search(@org.springframework.data.repository.query.Param("q") String q);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
