package com.salon.dto;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import com.salon.constant.*;

public final class StoreManagerRequests {
    private StoreManagerRequests() {
    }
    public record StaffInput(@NotBlank @Size(max=255) String name,
    @NotBlank @Email @Size(max=255) String email,
    @NotBlank @Pattern(regexp="[0-9]{10}",message="Mobile number must contain exactly 10 digits.") String mobileNo,
    @NotBlank @Size(max=255) String address, @Size(max=72) String password) {
    }
}
