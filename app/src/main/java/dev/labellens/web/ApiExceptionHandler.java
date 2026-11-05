package dev.labellens.web;

import dev.labellens.product.InvalidUploadException;
import dev.labellens.product.ProductNotFoundException;
import dev.labellens.product.UploadLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns our exceptions into standard error responses (RFC 9457 "problem details"):
 * { "type": ..., "title": "Bad Request", "status": 400, "detail": "Send at least 1 photo." }
 * Spring's own errors (missing field, file too large...) use the same format,
 * thanks to spring.mvc.problemdetails.enabled in application.yml.
 *
 * JSON API only (annotations = RestController.class): the web pages handle their own errors
 * with a message on the page (WebController).
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidUploadException.class)
    public ProblemDetail invalidUpload(InvalidUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UploadLimitException.class)
    public ProblemDetail uploadLimit(UploadLimitException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail notFound(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
