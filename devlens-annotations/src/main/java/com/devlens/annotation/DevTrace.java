package com.devlens.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DevTrace {

    long threshold() default 0;


    String[] maskFields() default {};


    int maxOutputLength() default -1;


    boolean includeStackTrace() default true;
}
