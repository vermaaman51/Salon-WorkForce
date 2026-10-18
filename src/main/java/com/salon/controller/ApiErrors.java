package com.salon.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> integrity(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","This record conflicts with existing data. Refresh and check linked records or duplicate email addresses."));
    }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> conflict(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Record not found":e.getReason()));
    }
    @ExceptionHandler( {
        MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class
    }) ResponseEntity<?> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message","Check required fields, dates and field lengths."));
    }
}
