package com.example.pptbackend.security;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/api/health", "/actuator/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/auth/editor-access-request").hasRole("VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/auth/editor-access-request/status").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/config/llm-api-key-presets").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.GET, "/api/config").authenticated()
                .requestMatchers("/api/config/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/index/segments").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/external-sources/load").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/projects/**").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/projects").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PUT, "/api/projects/**").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/projects/**").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/projects/**").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/evaluations/**").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/evaluations").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/projects/*/slides/*/evaluations").hasAnyRole("ADMIN", "EDITOR")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"请先登录\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    String detail = accessDeniedException.getMessage();
                    String msg = "无权执行此操作";
                    if (detail != null && !detail.isBlank() && !detail.contains("Access is denied")) {
                        msg = detail;
                    }
                    response.getWriter().write("{\"message\":\"" + msg.replace("\"", "\\\"") + "\"}");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
