package com.naukriradar.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the service as RFC 9457 Problem Details. The parent class covers
 * Spring's own exceptions (bad JSON, missing multipart part, upload limit, ...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BadRequestException.class)
	ProblemDetail handleBadRequest(BadRequestException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
		return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage());
	}

	@ExceptionHandler(BusinessRuleException.class)
	ProblemDetail handleBusinessRule(BusinessRuleException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", ex.getMessage());
	}

	@ExceptionHandler(UnauthenticatedException.class)
	ProblemDetail handleUnauthenticated(UnauthenticatedException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Unauthenticated", ex.getMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent update",
				"The resource was changed by another request. Reload it and try again.");
	}

	/** Last resort: log the details, but never send internals to the client. */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
				"Something went wrong on our side. Please try again.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
				"One or more fields are invalid.");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
