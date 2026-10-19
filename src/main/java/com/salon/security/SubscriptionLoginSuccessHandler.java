package com.salon.security;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import com.salon.service.TenantSubscriptions;
@Component
public class SubscriptionLoginSuccessHandler implements AuthenticationSuccessHandler {
 private final TenantSubscriptions subscriptions;
 public SubscriptionLoginSuccessHandler(TenantSubscriptions subscriptions){this.subscriptions=subscriptions;}
 public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws IOException,ServletException{
  if(auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))request.getSession().setAttribute("subscriptionSnapshot",subscriptions.subscriptions(UUID.fromString(auth.getName())));
  response.sendRedirect("/workspace");
 }
}
