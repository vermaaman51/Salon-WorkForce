package com.salon.utility;
import java.util.Locale;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
public final class GstNumber {
 private GstNumber(){}
 public static String normalize(String value){if(value==null||value.isBlank())return null;String result=value.trim().toUpperCase(Locale.ROOT);if(!result.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"GST number must be a valid 15-character GSTIN, or left blank.");return result;}
}
