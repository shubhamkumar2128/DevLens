package com.devlens.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;


public class InterceptionContext {

    private final Method method;
    private final Object[] arguments;
    private final String[] parameterNames;
    private final Class<?> targetClass;
    private final Set<Annotation> annotations;
    private final ProceedFunction proceedFunction;

    private InterceptionContext(Builder builder) {
        this.method = builder.method;
        this.arguments = builder.arguments;
        this.parameterNames = builder.parameterNames;
        this.targetClass = builder.targetClass;
        this.annotations = builder.annotations;
        this.proceedFunction = builder.proceedFunction;
    }

    public Method getMethod() { return method; }
    public Object[] getArguments() { return arguments; }
    public String[] getParameterNames() { return parameterNames; }
    public Class<?> getTargetClass() { return targetClass; }
    public Set<Annotation> getAnnotations() { return annotations; }
    public ProceedFunction getProceedFunction() { return proceedFunction; }

    public String getClassName() {
        return targetClass != null ? targetClass.getSimpleName() : method.getDeclaringClass().getSimpleName();
    }

    public String getMethodName() {
        return method.getName();
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return (A) annotation;
            }
        }
        return method.getAnnotation(annotationType);
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return true;
            }
        }
        return method.isAnnotationPresent(annotationType);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Method method;
        private Object[] arguments = new Object[0];
        private String[] parameterNames = new String[0];
        private Class<?> targetClass;
        private Set<Annotation> annotations = Set.of();
        private ProceedFunction proceedFunction;

        public Builder method(Method method) { this.method = method; return this; }
        public Builder arguments(Object[] arguments) { this.arguments = arguments != null ? arguments : new Object[0]; return this; }
        public Builder parameterNames(String[] parameterNames) { this.parameterNames = parameterNames != null ? parameterNames : new String[0]; return this; }
        public Builder targetClass(Class<?> targetClass) { this.targetClass = targetClass; return this; }
        public Builder annotations(Set<Annotation> annotations) { this.annotations = annotations != null ? annotations : Set.of(); return this; }
        public Builder proceedFunction(ProceedFunction proceedFunction) { this.proceedFunction = proceedFunction; return this; }

        public InterceptionContext build() {
            if (method == null) throw new IllegalStateException("Method must be set");
            if (proceedFunction == null) throw new IllegalStateException("ProceedFunction must be set");
            return new InterceptionContext(this);
        }
    }
}
