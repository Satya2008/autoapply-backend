package com.naukriradar.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

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
 * Turns every error into an RFC 9457 Problem Details body, so clients handle one shape.
 * Spring's own exceptions (bad JSON, wrong method, and so on) are covered by the parent
 * class; this class adds the application's exceptions and field-level validation errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
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
