package com.dms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupervisorProfileRepository extends JpaRepository<SupervisorProfile, Long> {
    Optional<SupervisorProfile> findByUser(User user);
}
