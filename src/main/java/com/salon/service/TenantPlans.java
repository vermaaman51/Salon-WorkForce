package com.salon.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** All limits are tenant-wide. Call reserve inside the same transaction as the insert. */
@Service
public class TenantPlans {
 public enum Plan {
  BASE(1,1,1,false,false), PRO(3,3,3,true,false), PRO_PLUS(7,14,14,true,true);
  public final int stores,managers,receptionists; public final boolean invoices,csv;
  Plan(int stores,int managers,int receptionists,boolean invoices,boolean csv){this.stores=stores;this.managers=managers;this.receptionists=receptionists;this.invoices=invoices;this.csv=csv;}
 }
 private final JdbcTemplate db;
 public TenantPlans(JdbcTemplate db){this.db=db;}
 public Plan plan(UUID tenant){return Plan.valueOf(db.queryForObject("SELECT segment_type FROM tenant WHERE id=?",String.class,tenant));}
 public void reserve(UUID tenant,String resource){
  var rows=db.queryForList("SELECT segment_type FROM tenant WHERE id=? FOR UPDATE",tenant);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant not found.");
  Plan p=Plan.valueOf(rows.get(0).get("segment_type").toString());
  int limit=switch(resource){case "stores"->p.stores;case "STORE_MANAGER"->p.managers;case "RECEPTIONIST"->p.receptionists;default->throw new IllegalArgumentException("Unsupported resource");};
  long count=resource.equals("stores")?db.queryForObject("SELECT count(*) FROM store WHERE tenant_id=?",Long.class,tenant):db.queryForObject("SELECT count(*) FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.tenant_id=? AND r.roletype=?",Long.class,tenant,resource);
  if(count>=limit)throw new ResponseStatusException(HttpStatus.CONFLICT,p.name().replace('_',' ')+" plan allows "+limit+" "+resource.toLowerCase().replace('_',' ')+" per tenant. Remove an existing record or contact the platform owner to upgrade.");
 }
 public Map<String,Object> metadata(UUID tenant){Plan p=plan(tenant);return Map.of("segmentType",p.name(),"stores",p.stores,"managers",p.managers,"receptionists",p.receptionists,"invoices",p.invoices,"csv",p.csv);}
}
