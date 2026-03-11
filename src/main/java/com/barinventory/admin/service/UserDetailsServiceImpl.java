package com.barinventory.admin.service;

import com.barinventory.admin.entity.User;
import com.barinventory.admin.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        log.info("Attempting to load user by email: {}", email);
        

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found");
                });

        if (!user.getActive()) {
            log.warn("Disabled account login attempt: {}", email);
            throw new UsernameNotFoundException("Account is disabled");
        }

        log.info("User loaded successfully: {} with role: {}", email, user.getRole());
        
        log.info("User active: {}, role: {}, barId: {}, passwordHash: {}", 
        	    user.getActive(), 
        	    user.getRole(), 
        	    user.getBarId(),
        	    user.getPassword().substring(0, 10)); // just first 10 chars

        // ✅ RETURN YOUR ENTITY DIRECTLY
        return user;
    }
}