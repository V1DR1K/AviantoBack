package com.avianto.back;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NotFoundException extends RuntimeException { NotFoundException(String message) { super(message); } }
class BusinessException extends RuntimeException { final int status; BusinessException(int status,String message) { super(message); this.status=status; } }
@RestControllerAdvice class Errors {
  private static final Logger log=LoggerFactory.getLogger(Errors.class);
  @ExceptionHandler(NotFoundException.class) ResponseEntity<?> notFound(NotFoundException e) { return error(404,e.getMessage()); }
  @ExceptionHandler(BusinessException.class) ResponseEntity<?> business(BusinessException e) { return error(e.status,e.getMessage()); }
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> constraint(DataIntegrityViolationException e) { return error(409,"La operación viola una restricción de integridad"); }
  @ExceptionHandler(ConstraintViolationException.class) ResponseEntity<?> constraintValidation(ConstraintViolationException e) { return error(400,"Datos inválidos"); }
  @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformedRequest(Exception e) { return error(400,"Datos inválidos"); }
  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<?> validation(org.springframework.web.bind.MethodArgumentNotValidException e) { return error(400,e.getBindingResult().getAllErrors().getFirst().getDefaultMessage()); }
  @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) { log.error("Error no controlado",e); return error(500,"Error interno"); }
  private ResponseEntity<?> error(int status,String message) { return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now(),"status",status,"message",message)); }
}
