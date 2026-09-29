package com.ffs.efdl.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.dto.ApiDtos.ErrorDto;

/** Réponses d'erreur JSON homogènes, sans trace ni détail interne. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorDto> unauthorized(AuthenticationException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorDto> forbidden(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "Vous ne pouvez pas accéder à cette page");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorDto> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldError() == null ? null : e.getBindingResult().getFieldError().getField();
        return error(HttpStatus.BAD_REQUEST, field == null ? "Données invalides" : "Champ invalide : " + field);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorDto> status(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return error(status == null ? HttpStatus.BAD_REQUEST : status, e.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorDto> unreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Données invalides");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorDto> unexpected(Exception e) {
        ResponseStatus status = e.getClass().getAnnotation(ResponseStatus.class);
        if (status != null) {
            return error(status.value(), e.getMessage());
        }
        log.error("Erreur inattendue", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne");
    }

    private static ResponseEntity<ErrorDto> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorDto(status.value(), status.name(), message));
    }
}
