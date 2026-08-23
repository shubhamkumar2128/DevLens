package com.devlens.core.serialization;


public class ToStringSerializer implements ObjectSerializer {

    @Override
    public String serialize(Object object) {
        return object == null ? "null" : object.toString();
    }
}
