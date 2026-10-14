package dev.workboard.api;

import dev.workboard.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ProblemDetail> domain(DomainException error, HttpServletRequest request) {
    int status =
        switch (error.code()) {
          case NOT_FOUND -> 404;
          case CONFLICT -> 409;
          case INVALID_TRANSITION -> 422;
          case INVALID_CREDENTIALS -> 401;
          case INVALID_REQUEST -> 422;
        };
    return problem(status, error.code().name(), error.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException error, HttpServletRequest request) {
    var result = problem(422, "VALIDATION_ERROR", "Invalid request.", request);
    result
        .getBody()
        .setProperty(
            "errors",
            error.getBindingResult().getFieldErrors().stream()
                .map(
                    e ->
                        Map.of(
                            "field",
                            e.getField(),
                            "message",
                            e.getDefaultMessage() == null
                                ? "Invalid value"
                                : e.getDefaultMessage()))
                .toList());
    return result;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> parameterValidation(
      ConstraintViolationException error, HttpServletRequest request) {
    return problem(422, "VALIDATION_ERROR", "Invalid request parameters.", request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> malformed(Exception error, HttpServletRequest request) {
    return problem(400, "INVALID_REQUEST", "Malformed request or invalid parameter type.", request);
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class
  })
  public ResponseEntity<ProblemDetail> conflict(Exception error, HttpServletRequest request) {
    return problem(409, "CONFLICT", "The write conflicts with existing data.", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
    if (error instanceof ErrorResponse response) {
      return problem(
          response.getStatusCode().value(),
          "HTTP_ERROR",
          "Request could not be processed.",
          request);
    }
    log.error("request_failed errorType={}", error.getClass().getSimpleName());
    return problem(500, "INTERNAL_ERROR", "An unexpected error occurred.", request);
  }

  private ResponseEntity<ProblemDetail> problem(
      int status, String code, String detail, HttpServletRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    body.setType(URI.create("urn:workboard:error:" + code.toLowerCase(java.util.Locale.ROOT)));
    body.setInstance(URI.create(request.getRequestURI()));
    body.setProperty("code", code);
    body.setProperty("requestId", request.getAttribute("requestId"));
    var builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (status == 401) builder.header("WWW-Authenticate", "Bearer");
    return builder.body(body);
  }
}
