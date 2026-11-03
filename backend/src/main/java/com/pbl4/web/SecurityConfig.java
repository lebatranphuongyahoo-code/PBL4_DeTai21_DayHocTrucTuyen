package com.pbl4.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, Database db) throws Exception {
        // Only Authorization bearer headers authenticate requests; cookies are never credentials.
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .authorizeHttpRequests(a -> a.requestMatchers("/","/index.html","/styles.css","/assets/**","/audio-capture.js","/favicon.ico","/error","/api/health","/api/auth/register","/api/auth/login","/api/auth/session","/ws/classroom").permitAll().anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,error) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"ok\":false,\"message\":\"Cần đăng nhập để tiếp tục.\"}");
            }))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
                    String header=req.getHeader("Authorization");
                    if (header!=null && header.startsWith("Bearer ") && header.length()<128) {
                        try {
                            Database.Account account=db.sessionAccount(AuthService.tokenHash(header.substring(7)));
                            if (account!=null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                        } catch (java.sql.SQLException e) { throw new ServletException("Session lookup failed",e); }
                    }
                    chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class).build();
    }
}
