package com.salon.utility;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MIN_CHARACTERS = 12;
    public static final int MAX_UTF8_BYTES = 72;
    private PasswordPolicy() {
    }
    public static boolean isValid(String value) {
        return value != null && value.length() >= MIN_CHARACTERS
        && value.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }
}
