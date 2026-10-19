package com.salon;

import java.sql.*;
import java.util.*;
import org.h2.api.Trigger;

/** H2 equivalent of the PostgreSQL audit triggers, for integration tests. */
public class OrderHistoryTestTrigger implements Trigger {
    private boolean immutable;
    private final Map<String,Integer> columns=new HashMap<>();
    @Override public void init(Connection connection,String schema,String trigger,String table,boolean before,int type) throws SQLException {
        immutable=table.equalsIgnoreCase("order_history");
        try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT * FROM "+table+" WHERE 1=0")) {
            var metadata=rows.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++)columns.put(metadata.getColumnName(i).toLowerCase(Locale.ROOT),i-1);
        }
    }
    @Override public void fire(Connection connection,Object[] oldRow,Object[] newRow) throws SQLException {
        if(immutable)throw new SQLException("order_history is append-only","55000");
        Object[] row=newRow==null?oldRow:newRow;
        String[] baseNames={"id","tenant_id","store_id","assignee","order_type","order_status","payment_status","customer_name","customer_mobile","details","service","amount","appointment_at","created_at","payment_mode"};
        var names=new ArrayList<>(Arrays.asList(baseNames));if(columns.containsKey("modified_at"))names.add("modified_at");
        if(columns.containsKey("payment_completed_at"))names.add("payment_completed_at");
        String sql="INSERT INTO order_history(order_id,"+String.join(",",names.subList(1,names.size()))+",event_type) VALUES ("+String.join(",",Collections.nCopies(names.size()+1,"?"))+")";
        try(var insert=connection.prepareStatement(sql)) {
            for(int i=0;i<names.size();i++)insert.setObject(i+1,row[columns.get(names.get(i))]);
            insert.setString(names.size()+1,newRow==null?"DELETE":oldRow==null?"INSERT":"UPDATE");
            insert.executeUpdate();
        }
    }
}
