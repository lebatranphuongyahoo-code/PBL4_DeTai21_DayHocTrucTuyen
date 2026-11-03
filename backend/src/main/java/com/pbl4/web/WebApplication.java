package com.pbl4.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class WebApplication {
    public static void main(String[] args) { SpringApplication.run(WebApplication.class, args); }
}
