package com.salon.utility;
import java.io.IOException;
import java.sql.Timestamp;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;
/** Database timestamps represent the salon's local wall clock, not UTC instants. */
@JsonComponent
public class SalonTimeJson extends JsonSerializer<Timestamp> {
 @Override public void serialize(Timestamp value,JsonGenerator generator,SerializerProvider provider) throws IOException {
  generator.writeString(value.toLocalDateTime().toString());
 }
}
