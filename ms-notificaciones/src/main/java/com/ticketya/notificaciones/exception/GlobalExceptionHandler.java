package com.ticketya.notificaciones.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ticketya.notificaciones.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Traduce todas las excepciones al formato {@code Error} de openapi.yaml.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos invalidos", req, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<String> details = ex.getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos invalidos", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos invalidos", req, details);
    }

    /** Cuerpo mal formado o con un valor que no corresponde, por ejemplo un {@code tipo} desconocido. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            String campo = ife.getPath().stream()
                    .map(r -> r.getFieldName() == null ? "[" + r.getIndex() + "]" : r.getFieldName())
                    .collect(Collectors.joining("."));
            return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos invalidos", req,
                    List.of(campo + " tiene un valor no admitido: " + ife.getValue()));
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "El cuerpo de la solicitud es invalido o esta mal formado", req, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "El header " + ex.getHeaderName() + " es obligatorio", req, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Falta el parametro " + ex.getParameterName(), req, null);
    }

    /** Filtros {@code tipo} o {@code estado} con un valor que no existe, o {@code page}/{@code size} no numericos. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Valor invalido para " + ex.getName() + ": " + ex.getValue(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No existe el recurso solicitado", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Metodo " + ex.getMethod() + " no soportado", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrio un error inesperado", req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest req, List<String> details) {
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), code, message,
                req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
