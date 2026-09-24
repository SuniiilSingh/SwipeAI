package com.match.SwipeAI.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tracking payment transactions via AOP.
 * Captures execution duration, status, and concise one-line success/failure summaries without stack traces.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackPaymentTransaction {
    String action() default "";
}
