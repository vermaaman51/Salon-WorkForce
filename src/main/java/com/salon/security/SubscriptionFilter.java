package com.salon.security;
import com.salon.service.TenantSubscriptions;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
public class SubscriptionFilter extends OncePerRequestFilter {
 private final TenantSubscriptions service;
 public SubscriptionFilter(TenantSubscriptions service){this.service=service;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
 var a=SecurityContextHolder.getContext().getAuthentication();
 if(a!=null&&a.isAuthenticated()&&!(a instanceof AnonymousAuthenticationToken)&&!req.getRequestURI().equals("/logout")&&a.getName().matches("[0-9a-fA-F-]{36}")&&!service.allowed(UUID.fromString(a.getName()))){if(req.getSession(false)!=null)req.getSession(false).invalidate();SecurityContextHolder.clearContext();if(req.getRequestURI().startsWith("/api/")){res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Your subscription has expired. Contact your administrator.\"}");}else res.sendRedirect("/login?expired");return;}chain.doFilter(req,res);
 }
}
