package com.dms.security;

import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.user.User;
import com.dms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("authz")
@RequiredArgsConstructor
public class AuthzService {
    private final UserRepository userRepository;
    private final AllocationRepository allocationRepository;
    public boolean isSelf(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Optional<User> current = userRepository.findByEmail(authentication.getName());
        return current.isPresent() && current.get().getId().equals(userId);
    }
    public Optional<User> currentUser(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    /**
     * True when the signed-in guide actually supervises this student.
     *
     * <p>Scoped to the statuses that occupy a seat. A REQUESTED allocation is not
     * supervision yet -- treating it as such would hand a guide read access to a
     * student who has merely named them.
     */
    public boolean supervises(Long studentId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return allocationRepository.existsByStudentIdAndSupervisorUserEmailAndStatusIn(
                studentId, authentication.getName(), AllocationStatus.OCCUPIES_A_SEAT);
    }
    public boolean ownsSubmission(Long submissionId, Authentication authentication) {
        return false;
    }
}
