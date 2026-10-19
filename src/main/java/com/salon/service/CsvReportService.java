package com.salon.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpStatus;
import com.salon.repository.AdminRepository;
import com.salon.repository.StoreManagerRepository;
import com.salon.utility.CurrentUser;

@Service
public class CsvReportService {
 private final JdbcTemplate db;private final AdminRepository admins;private final StoreManagerRepository managers;private final ReportDates dates;private final TransactionTemplate transactions;
 private static final Pattern FORMULA=Pattern.compile("^[\\s\\uFEFF]*[=+\\-@]|^[\\t\\r\\n]");
 public record Export(String filename,StreamingResponseBody body){}
 public CsvReportService(JdbcTemplate db,AdminRepository admins,StoreManagerRepository managers,ReportDates dates,PlatformTransactionManager transactionManager){this.db=db;this.admins=admins;this.managers=managers;this.dates=dates;this.transactions=new TransactionTemplate(transactionManager);this.transactions.setReadOnly(true);}
 @PreAuthorize("hasRole('ADMIN')")
 public Export admin(Authentication auth,java.time.LocalDate from,java.time.LocalDate to,UUID store){
  var tenants=admins.findAdminTenants(CurrentUser.id(auth));if(tenants.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A single Admin tenant assignment is required.");UUID tenant=tenants.get(0);
  requireSubscription(tenant);var range=dates.resolve(null,from,to);
  if(store!=null&&!admins.storeBelongsToTenant(store,tenant))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Store not found.");
  return prepare(tenant,store,range,false);
 }
 @PreAuthorize("hasRole('STORE_MANAGER')")
 public Export manager(Authentication auth,UUID store){
  var tenants=managers.findManagerTenants(CurrentUser.id(auth),store);if(tenants.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Assigned store not found.");UUID tenant=tenants.get(0);requireSubscription(tenant);
  // The day is always derived on the server when the request is made.
  return prepare(tenant,store,dates.resolve(null,null,null),true);
 }
 private void requireSubscription(UUID tenant){
  var rows=db.queryForList("SELECT segment_type FROM tenant WHERE id=? AND _is_active=TRUE AND (subscription_expires_at IS NULL OR subscription_expires_at>CURRENT_TIMESTAMP)",tenant);
  if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Your subscription has expired. Contact your administrator.");
  if(!"PRO_PLUS".equals(rows.get(0).get("segment_type")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"CSV reports require an active Pro Plus subscription.");
 }
 private Export prepare(UUID tenant,UUID store,ReportDates.Range range,boolean manager){
  String filename=(manager?"today-store-sales-":"sales-")+range.from()+"-to-"+range.to()+".csv";
  return new Export(filename,output->{
   try{transactions.executeWithoutResult(transaction->{
    requireSubscription(tenant);
    var writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
    try{writer.write('\uFEFF');writeRow(writer,manager?List.of("Store","Appointment date/time","Customer name","Service","Amount","Order status","Payment status","Expert"):List.of("Store","Appointment date/time","Customer name","Service","Amount","Order status","Payment status"));
     String sql="SELECT s.name AS store_name,o.appointment_at,o.customer_name,o.service,o.amount,o.order_status,o.payment_status"+(manager?",u.name AS expert_name":"")+" FROM orders o JOIN store s ON s.id=o.store_id AND s.tenant_id=o.tenant_id"+(manager?" LEFT JOIN user_role ur ON ur.id=o.assignee AND ur.store_id=o.store_id AND ur.tenant_id=o.tenant_id LEFT JOIN users u ON u.id=ur.user_id":"")+" WHERE o.tenant_id=? AND o.order_type='APPOINTMENT' AND o.appointment_at>=? AND o.appointment_at<?"+(store==null?"":" AND o.store_id=?")+" ORDER BY o.modified_at DESC,o.id DESC";
     db.query(connection->{var statement=connection.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);statement.setFetchSize(200);statement.setObject(1,tenant);statement.setObject(2,range.from().atStartOfDay());statement.setObject(3,range.to().plusDays(1).atStartOfDay());if(store!=null)statement.setObject(4,store);return statement;},(org.springframework.jdbc.core.RowCallbackHandler)row->{
      try{var values=new ArrayList<String>();values.add(row.getString("store_name"));values.add(row.getTimestamp("appointment_at").toLocalDateTime().toString());values.add(row.getString("customer_name"));values.add(row.getString("service"));values.add(row.getBigDecimal("amount").toPlainString());values.add(label(row.getString("order_status")));values.add("COMPLETED".equals(row.getString("payment_status"))?"Paid":"Pending");if(manager)values.add(row.getString("expert_name"));writeRow(writer,values);}catch(IOException ex){throw new UncheckedIOException(ex);}
     });writer.flush();
    }catch(IOException ex){throw new UncheckedIOException(ex);}
   });}catch(UncheckedIOException ex){throw ex.getCause();}
  });
 }
 private static String label(String status){return switch(status){case "PENDING"->"Pending";case "ASSIGNED"->"Assigned";case "COMPLETED"->"Completed";default->status;};}
 public static String cell(String value){String text=value==null?"":value;if(FORMULA.matcher(text).find())text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
 private static void writeRow(Writer writer,List<String> values)throws IOException{for(int i=0;i<values.size();i++){if(i>0)writer.write(',');writer.write(cell(values.get(i)));}writer.write("\r\n");}
}
