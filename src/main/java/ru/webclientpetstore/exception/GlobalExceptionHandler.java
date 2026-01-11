package ru.webclientpetstore.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Для простых параметров (@PathVariable, @RequestParam)
    // Именно это исключение вы видели в своем логе
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String paramName = path.substring(path.lastIndexOf('.') + 1);
                    return paramName + ": " + violation.getMessage();
                })
                .collect(Collectors.joining(", "));

        return createProblemDetail(HttpStatus.BAD_REQUEST, "Validation Failed", details);
    }

    // 2. Для новых механизмов Spring 6.2+
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidationException(HandlerMethodValidationException ex) {
        StringJoiner sj = new StringJoiner(", ");
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error -> sj.add(error.getDefaultMessage()))
        );

        return createProblemDetail(HttpStatus.BAD_REQUEST, "Validation Failed", sj.toString());
    }

    // 3. Для объектов в теле запроса (@RequestBody)
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleBindException(WebExchangeBindException ex) {
        String details = ex.getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Request Body", details);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex, ServerWebExchange exchange) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        pd.setTitle("Сущность не найдена");
        pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return pd;
    }


    // Вспомогательный метод для единообразия
    private ProblemDetail createProblemDetail(HttpStatus status, String title, String details) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, details);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}