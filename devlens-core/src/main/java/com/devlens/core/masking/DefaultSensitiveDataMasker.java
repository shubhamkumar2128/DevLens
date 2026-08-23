package com.devlens.core.masking;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of SensitiveDataMasker.
 * Replaces field values with "***" for matching field names (case-insensitive).
 * Thread-safe.
 */
public class DefaultSensitiveDataMasker implements SensitiveDataMasker {

    private static final String MASK_VALUE = "***";

    private static final Set<String> DEFAULT_FIELDS = Set.of(
            "password", "passwd", "token", "authorization",
            "accesstoken", "refreshtoken", "secret", "cardnumber", "cvv"
    );

    private final Set<String> configuredFields;

    public DefaultSensitiveDataMasker() {
        this.configuredFields = new HashSet<>();
    }

    public DefaultSensitiveDataMasker(List<String> additionalConfiguredFields) {
        this.configuredFields = new HashSet<>();
        if (additionalConfiguredFields != null) {
            for (String field : additionalConfiguredFields) {
                this.configuredFields.add(field.toLowerCase());
            }
        }
    }

    @Override
    public String mask(String serialized, String... additionalFields) {
        if (serialized == null || serialized.isEmpty()) {
            return serialized;
        }

        Set<String> allFields = new HashSet<>(DEFAULT_FIELDS);
        allFields.addAll(configuredFields);
        if (additionalFields != null) {
            for (String field : additionalFields) {
                if (field != null && !field.isEmpty()) {
                    allFields.add(field.toLowerCase());
                }
            }
        }

        String result = serialized;
        for (String field : allFields) {
            // Match patterns like: "fieldName":"value" or "fieldName" : "value" or fieldName=value
            String quotedPattern = "(?i)(\"?" + Pattern.quote(field) + "\"?\\s*[:=]\\s*)\"[^\"]*\"";
            result = result.replaceAll(quotedPattern, "$1\"" + MASK_VALUE + "\"");

            // Match patterns like: fieldName=value (without quotes, terminated by comma/space/bracket/end)
            String unquotedPattern = "(?i)(\"?" + Pattern.quote(field) + "\"?\\s*[:=]\\s*)([^,\\s}\\]\"]+)";
            result = result.replaceAll(unquotedPattern, "$1" + MASK_VALUE);
        }

        return result;
    }
}
