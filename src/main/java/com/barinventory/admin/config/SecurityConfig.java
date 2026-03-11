package com.barinventory.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ✅ Disable CSRF only for REST API endpoints (fetch calls from JS)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/sessions/wells/*/save")
            )
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // ✅ Public static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // ✅ Public pages
                .requestMatchers("/login", "/error", "/").permitAll()
                // ✅ Dashboard - all authenticated roles
                .requestMatchers("/dashboard").hasAnyRole("ADMIN", "BAR_OWNER", "BAR_STAFF")
                // ✅ Admin only
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // ✅ Bar management
                .requestMatchers("/bars/new", "/bars/edit/**").hasAnyRole("ADMIN", "BAR_OWNER")
                // ✅ Inventory flows
                .requestMatchers(
                    "/sessions/**",
                    "/stockroom/**",
                    "/inventory/**",
                    "/api/**"           // ✅ covers /api/sessions/{id}/commit
                ).hasAnyRole("ADMIN", "BAR_OWNER", "BAR_STAFF")
                // ✅ Everything else requires login
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            // ✅ Proper 403 handler - redirects to login with error instead of whitelabel
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String uri = request.getRequestURI();
                    // ✅ API calls get JSON error, not redirect
                    if (uri.startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.getWriter().write(
                            "{\"error\":\"Forbidden\",\"path\":\"" + uri + "\"}"
                        );
                    } else {
                        response.sendRedirect("/login?error=403");
                    }
                })
            );

        return http.build();
    }
}