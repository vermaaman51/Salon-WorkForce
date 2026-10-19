package com.salon.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.salon.utility.CurrentUser;
@Service
public class InvoiceService {
 private final JdbcTemplate db;private final TenantPlans plans;
 public InvoiceService(JdbcTemplate db,TenantPlans plans){this.db=db;this.plans=plans;}
 @org.springframework.transaction.annotation.Transactional
 @PreAuthorize("hasAnyRole('STORE_MANAGER','RECEPTIONIST')")
 public Object invoice(Authentication auth,UUID store,UUID order,String role){
  if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_"+role)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var scopes=db.queryForList("SELECT DISTINCT s.tenant_id FROM store s JOIN user_role ur ON ur.store_id=s.id AND ur.tenant_id=s.tenant_id JOIN role r ON r.id=ur.role_id WHERE s.id=? AND ur.user_id=? AND r.roletype=?",UUID.class,store,CurrentUser.id(auth),role);
  if(scopes.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Assigned store not found.");UUID tenant=scopes.get(0);
  if(!plans.plan(tenant).invoices)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Printable invoices require Pro or Pro Plus.");
  db.queryForList("SELECT id FROM orders WHERE id=? AND store_id=? AND tenant_id=? FOR UPDATE",order,store,tenant);
  var rows=db.queryForList("SELECT o.id,o.customer_name,o.customer_mobile,o.service,o.amount,o.appointment_at,o.payment_status,o.payment_mode,s.name AS store_name,s.address AS store_address,s.city,s.gst_number,t.tenant_name FROM orders o JOIN store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id JOIN tenant t ON t.id=o.tenant_id WHERE o.id=? AND o.store_id=? AND o.tenant_id=? AND o.order_type='APPOINTMENT' AND o.order_status='COMPLETED'",order,store,tenant);
  if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Completed appointment not found.");if(db.queryForList("SELECT invoice_number FROM order_invoice WHERE order_id=?",order).isEmpty())db.update("INSERT INTO order_invoice(order_id) VALUES (?)",order);
  long number=db.queryForObject("SELECT invoice_number FROM order_invoice WHERE order_id=?",Long.class,order);
  var result=new LinkedHashMap<String,Object>(rows.get(0));result.put("invoice_no",String.format(java.util.Locale.ROOT,"INV-%06d",number));return result;
 }
}
