package com.naukriradar.common.exception;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A query/path parameter rejected by {@code @Validated} raises {@link ConstraintViolationException},
 * which used to fall through to the 500 handler. It must come back as a plain 400 with the bare
 * parameter name (not "method.param").
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	record Query(@Size(max = 3) String q) {
	}

	@Test
	void constraintViolationBecomesA400WithTheFieldName() {
		Set<ConstraintViolation<Query>> violations = validator.validate(new Query("too long"));
		assertThat(violations).isNotEmpty();

		ProblemDetail body = handler.handleConstraintViolation(new ConstraintViolationException(violations));

		assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		@SuppressWarnings("unchecked")
		var errors = (java.util.Map<String, String>) body.getProperties().get("errors");
		assertThat(errors).containsKey("q");
	}
}
