package com.devlens.spring.aspect;

import com.devlens.core.DevLensInterceptorEngine;
import com.devlens.core.InterceptionContext;
import com.devlens.core.ProceedFunction;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Aspect
public class DevLensAspect {

    private final DevLensInterceptorEngine engine;

    public DevLensAspect(DevLensInterceptorEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(com.devlens.annotation.ExecutionTime) || " +
            "@annotation(com.devlens.annotation.SlowMethod) || " +
            "@annotation(com.devlens.annotation.LogInput) || " +
            "@annotation(com.devlens.annotation.LogOutput) || " +
            "@annotation(com.devlens.annotation.LogException) || " +
            "@annotation(com.devlens.annotation.ThreadInfo) || " +
            "@annotation(com.devlens.annotation.CorrelationId) || " +
            "@annotation(com.devlens.annotation.ExternalCall) || " +
            "@annotation(com.devlens.annotation.Retry) || " +
            "@annotation(com.devlens.annotation.MemoryUsage) || " +
            "@annotation(com.devlens.annotation.DevTrace)")
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            InterceptionContext context = buildContext(joinPoint);
            return engine.intercept(context);
        } catch (Throwable t) {
            // If it's an application exception (from the method), let it propagate
            // Only catch DevLens-internal issues here
            if (isDevLensInternalException(t, joinPoint)) {
                // Fallback: just proceed with the original method
                return joinPoint.proceed();
            }
            throw t;
        }
    }

    private InterceptionContext buildContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : method.getDeclaringClass();

        // Collect all annotations from the method
        Set<Annotation> annotations = new HashSet<>(Arrays.asList(method.getAnnotations()));

        // Get parameter names
        String[] parameterNames = signature.getParameterNames();

        ProceedFunction proceedFunction = joinPoint::proceed;

        return InterceptionContext.builder()
                .method(method)
                .arguments(joinPoint.getArgs())
                .parameterNames(parameterNames)
                .targetClass(targetClass)
                .annotations(annotations)
                .proceedFunction(proceedFunction)
                .build();
    }

    private boolean isDevLensInternalException(Throwable t, ProceedingJoinPoint joinPoint) {
        // If the exception originates from DevLens packages, it's internal
        String packageName = t.getClass().getPackageName();
        return packageName.startsWith("com.devlens.core") || packageName.startsWith("com.devlens.spring");
    }
}
