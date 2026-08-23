package com.devlens.core;


@FunctionalInterface
public interface ProceedFunction {

    Object proceed() throws Throwable;
}
