package com.naukriradar.core.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records a call in the audit log, whether it succeeds or fails. {@link #targetId} and
 * {@link #detail} are SpEL over the method's parameters by name, plus {@code #result} for
 * the return value of a successful call.
 *
 * <p>Works through a Spring proxy: only on public methods of Spring beans, and not when a
 * method calls another method of its own class.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

	String action();

	String targetType() default "";

	String targetId() default "";

	/** Must never include secret values. */
	String detail() default "";

}
