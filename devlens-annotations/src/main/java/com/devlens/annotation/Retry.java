package com.devlens.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {


    int maxAttempts() default 3;


    long delay() default 1000;


    boolean exponentialBackoff() default false;


    Class<? extends Throwable>[] retryOn() default {};

    Class<? extends Throwable>[] noRetryOn() default {};
}
