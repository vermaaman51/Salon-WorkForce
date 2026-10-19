package com.salon;
import java.sql.*;
import org.h2.api.Trigger;
public class OrderModifiedTestTrigger implements Trigger {
 private int column,payment=-1,status=-1;
 public void init(Connection db,String schema,String trigger,String table,boolean before,int type)throws SQLException{try(var s=db.createStatement();var r=s.executeQuery("SELECT * FROM orders WHERE 1=0")){var m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++){String name=m.getColumnName(i);if(name.equalsIgnoreCase("modified_at"))column=i-1;if(name.equalsIgnoreCase("payment_completed_at"))payment=i-1;if(name.equalsIgnoreCase("payment_status"))status=i-1;}}}
 public void fire(Connection db,Object[] oldRow,Object[] newRow){var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);newRow[column]=now;if(payment>=0){boolean completed="COMPLETED".equals(newRow[status]);newRow[payment]=completed&&(oldRow==null||!"COMPLETED".equals(oldRow[status]))?now:oldRow==null?null:oldRow[payment];}}
}
