package com.naukriradar.core.audit;

import java.lang.reflect.Method;

import com.naukriradar.core.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

/**
 * Writes an audit entry around every {@link Audited} method. Ordered outside the transaction
 * advice, so a change that fails to commit is logged as failed, not as done. Who and where
 * are read here, on the request thread; the write itself is handed to {@link AuditService}
 * and happens off the request.
 */
@Aspect
@Component
@Order(0)
public class AuditAspect {

	private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

	private final ExpressionParser parser = new SpelExpressionParser();
	private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
	private final AuditService auditService;

	public AuditAspect(AuditService auditService) {
		this.auditService = auditService;
	}

	@Around("@annotation(audited)")
	public Object audit(ProceedingJoinPoint call, Audited audited) throws Throwable {
		RequestOrigin origin = RequestOrigin.current();
		Object result;
		try {
			result = call.proceed();
		}
		catch (Throwable ex) {
			record(call, audited, origin, null, false, ex.getMessage());
			throw ex;
		}
		record(call, audited, origin, result, true, null);
		return result;
	}

	private void record(ProceedingJoinPoint call, Audited audited, RequestOrigin origin, Object result, boolean success,
			String error) {
		try {
			Method method = ((MethodSignature) call.getSignature()).getMethod();
			MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(call.getTarget(), method,
					call.getArgs(), parameterNames);
			context.setVariable("result", result);
			auditService.record(origin, audited.action(), blankToNull(audited.targetType()),
					evaluate(audited.targetId(), context), evaluate(audited.detail(), context), success, error);
		}
		catch (RuntimeException ex) {
			// a broken audit expression must never break the action it describes
			log.error("Could not audit {}", audited.action(), ex);
		}
	}

	private String evaluate(String expression, MethodBasedEvaluationContext context) {
		if (expression.isBlank()) {
			return null;
		}
		Expression parsed = parser.parseExpression(expression);
		Object value;
		try {
			value = parsed.getValue(context);
		}
		catch (RuntimeException ex) {
			// e.g. #result.id() after a failure, when there is no result
			return null;
		}
		return value == null ? null : value.toString();
	}

	private static String blankToNull(String value) {
		return value.isBlank() ? null : value;
	}

}
