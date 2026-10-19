package com.salon.service;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class ReportDates {
 private final ZoneId zone;
 public ReportDates(@Value("${TZ:Asia/Kolkata}") String timezone){zone=ZoneId.of(timezone);}
 public record Range(LocalDate from,LocalDate to,LocalDate today,LocalDate minDate,ZoneId zone){}
 public Range resolve(LocalDate date,LocalDate from,LocalDate to){
  LocalDate today=LocalDate.now(zone),min=today.minusMonths(1);
  if(date!=null&&(from!=null||to!=null))bad("Use either a date or a From/To range.");
  if((from==null)!=(to==null))bad("Select both From and To dates.");
  if(from==null){from=date==null?today:date;to=from;}
  if(from.isBefore(min)||from.isAfter(to)||to.isAfter(today)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>=30)bad("Choose dates within the past month and a range of up to 30 days, including both dates.");
  return new Range(from,to,today,min,zone);
 }
 private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
