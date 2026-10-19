package com.salon.controller;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.salon.service.CsvReportService;
@RestController
public class CsvReportController {
 private final CsvReportService service;
 public CsvReportController(CsvReportService service){this.service=service;}
 @GetMapping("/api/admin/sales/export") public ResponseEntity<StreamingResponseBody> admin(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) UUID storeId){return response(service.admin(auth,from,to,storeId));}
 @GetMapping("/api/manager/sales/export") public ResponseEntity<StreamingResponseBody> manager(Authentication auth,@RequestParam UUID storeId){return response(service.manager(auth,storeId));}
 private ResponseEntity<StreamingResponseBody> response(CsvReportService.Export export){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(export.filename()).build().toString()).header("X-Content-Type-Options","nosniff").body(export.body());}
}
