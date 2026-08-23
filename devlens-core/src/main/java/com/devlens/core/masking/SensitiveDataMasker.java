package com.devlens.core.masking;


public interface SensitiveDataMasker {

    String mask(String serialized, String... additionalFields);
}
