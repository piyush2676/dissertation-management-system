package com.dms.security;

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

    public boolean supervises(Long studentId, Authentication authentication) {
        return false;
    }
    public boolean ownsSubmission(Long submissionId, Authentication authentication) {
        return false;
    }
}
