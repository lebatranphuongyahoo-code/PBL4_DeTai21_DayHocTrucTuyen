package com.pbl4.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(AuthService.Failure.class) public ResponseEntity<?> failure(AuthService.Failure e){return ResponseEntity.status(e.status).body(Map.of("ok",false,"message",e.getMessage(),"errors",e.errors));}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) public ResponseEntity<?> malformed(){return ResponseEntity.badRequest().body(Map.of("ok",false,"message","Dữ liệu gửi lên không hợp lệ."));}
}
