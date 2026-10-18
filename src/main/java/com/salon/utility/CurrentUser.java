package com.salon.utility;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {
    }
    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
