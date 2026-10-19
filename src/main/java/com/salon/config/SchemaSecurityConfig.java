package com.salon.config;

import com.salon.constant.Roles;
import com.salon.security.ApiSecurityHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import java.util.*;
import org.springframework.context.annotation.*;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
@Profile("!legacy")
public class SchemaSecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecurityFilterChain schemaSecurity(HttpSecurity http, ApiSecurityHandler securityHandler, com.salon.service.TenantSubscriptions subscriptions, com.salon.security.SubscriptionLoginSuccessHandler successHandler) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET,"/health","/login","/auth/csrf","/css/**","/js/auth.js","/js/form-validation.js","/","/index.html").permitAll()
            .requestMatchers("/api/root/**","/root","/js/root.js","/js/tenant-expiry.js").hasRole("ROOT")
            .requestMatchers("/js/subscription.js","/api/subscription").hasRole(Roles.ADMIN)
            .requestMatchers("/api/admin/**","/dashboard","/js/admin.js").hasRole(Roles.ADMIN)
            .requestMatchers("/api/manager/**","/manager/dashboard","/manager/appointments","/js/manager.js").hasRole(Roles.STORE_MANAGER)
            .requestMatchers("/js/workspace-plan.js").hasAnyRole(Roles.ADMIN,Roles.STORE_MANAGER,Roles.RECEPTIONIST)
            .requestMatchers("/js/appointment-dashboard.js").hasAnyRole(Roles.ADMIN,Roles.RECEPTIONIST,Roles.STORE_MANAGER)
            .requestMatchers("/js/reception.js").hasAnyRole(Roles.RECEPTIONIST,Roles.STORE_MANAGER)
            .requestMatchers("/api/reception/**","/reception/dashboard").hasRole(Roles.RECEPTIONIST)
            .requestMatchers("/workspace","/api/me").authenticated()
            .anyRequest().denyAll())
            .formLogin(f->f.loginPage("/login").usernameParameter("mobileNo").successHandler(successHandler).failureHandler((req,res,e)->res.sendRedirect(e instanceof org.springframework.security.authentication.DisabledException?"/login?expired":"/login?error")).permitAll())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(f->f.changeSessionId()))
            .logout(l->l.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
                .clearAuthentication(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(e->e.defaultAuthenticationEntryPointFor(securityHandler,req->req.getRequestURI().startsWith("/api/"))
                .accessDeniedHandler(securityHandler))
            .addFilterBefore(new com.salon.security.SubscriptionFilter(subscriptions),org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .build();
    }
}
