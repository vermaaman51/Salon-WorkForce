package com.salon.repository;

import java.util.*;
import java.time.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class OrderPages {
 public record Query(Integer page,String view,String type,String status,String payment,String expert,@org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date){public Query{if(page==null)page=0;}public static Query first(){return new Query(0,"total",null,null,null,null,null);}}
 private final JdbcTemplate db;
 public OrderPages(JdbcTemplate db){this.db=db;}
 private void choice(String value,Set<String> choices){if(value!=null&&!value.isBlank()&&!choices.contains(value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order filter.");}
 public Map<String,Object> list(UUID tenant,UUID store,Query q,boolean appointmentsOnly,LocalDateTime from,LocalDateTime to,boolean admin){
  if(q.page()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be zero or greater.");
  String view=q.view()==null||q.view().isBlank()?"total":q.view();
  choice(view,Set.of("total","unassigned","running","completed","sales","checkout"));choice(q.type(),Set.of("APPOINTMENT","QUERY"));choice(q.status(),Set.of("PENDING","ASSIGNED","COMPLETED"));choice(q.payment(),Set.of("PENDING","COMPLETED"));
  StringBuilder where=new StringBuilder(" WHERE tenant_id=?");List<Object> args=new ArrayList<>();args.add(tenant);
  if(store!=null){where.append(" AND store_id=?");args.add(store);}
  if(appointmentsOnly)where.append(" AND order_type='APPOINTMENT'");
  else if(q.type()!=null&&!q.type().isBlank()){where.append(" AND order_type=?");args.add(q.type());}
  if(q.status()!=null&&!q.status().isBlank()){where.append(" AND order_status=?");args.add(q.status());}
  if(q.payment()!=null&&!q.payment().isBlank()){where.append(" AND payment_status=?");args.add(q.payment());}
  if(q.expert()!=null&&!q.expert().isBlank()){if(q.expert().equals("UNASSIGNED"))where.append(" AND assignee IS NULL");else{try{args.add(UUID.fromString(q.expert()));}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid expert.");}where.append(" AND assignee=?");}}
  if(q.date()!=null){String column="QUERY".equals(q.type())?"created_at":"appointment_at";where.append(" AND "+column+">=? AND "+column+"<?");args.add(q.date().atStartOfDay());args.add(q.date().plusDays(1).atStartOfDay());}
  if(from!=null){where.append(" AND appointment_at>=? AND appointment_at<?");args.add(from);args.add(to);}
  var summary=db.queryForMap("SELECT count(CASE WHEN order_type='APPOINTMENT' THEN 1 END) AS total,count(CASE WHEN order_type='APPOINTMENT' AND assignee IS NULL THEN 1 END) AS unassigned,count(CASE WHEN order_type='APPOINTMENT' AND order_status IN ('PENDING','ASSIGNED') THEN 1 END) AS running,count(CASE WHEN order_type='APPOINTMENT' AND order_status='COMPLETED' THEN 1 END) AS completed,COALESCE(sum(CASE WHEN order_type='APPOINTMENT' AND order_status='COMPLETED' AND payment_status='COMPLETED' THEN amount ELSE 0 END),0) AS sales,COALESCE(sum(CASE WHEN order_type='APPOINTMENT' AND payment_status='PENDING' THEN amount ELSE 0 END),0) AS unpaid FROM orders"+where,args.toArray());
  String extra=switch(view){case "unassigned"->" AND assignee IS NULL";case "running"->" AND order_status IN ('PENDING','ASSIGNED')";case "completed"->" AND order_status='COMPLETED'";case "sales"->" AND order_status='COMPLETED' AND payment_status='COMPLETED'";case "checkout"->" AND order_type='APPOINTMENT' AND assignee IS NOT NULL";default->"";};
  long total=db.queryForObject("SELECT count(*) FROM orders"+where+extra,Long.class,args.toArray());
  var pageArgs=new ArrayList<>(args);pageArgs.add(20);pageArgs.add((long)q.page()*20);
  String columns=admin?"id,store_id,customer_name,service AS service_name,amount,order_status,payment_status,payment_completed_at,created_at,modified_at":"orders.*,service AS service_name";
  var items=db.queryForList("SELECT "+columns+" FROM orders"+where+extra+" ORDER BY modified_at DESC,id DESC LIMIT ? OFFSET ?",pageArgs.toArray());
  return new LinkedHashMap<>(Map.of("items",items,"page",q.page(),"pageSize",20,"total",total,"totalPages",(total+19)/20,"summary",summary));
 }
}
