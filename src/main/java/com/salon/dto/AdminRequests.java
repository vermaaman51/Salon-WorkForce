package com.salon.dto;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import com.salon.constant.*;

public final class AdminRequests {
    private AdminRequests() {
    }
    public record StoreInput(@NotBlank @Size(max=255) String name,@NotBlank @Size(max=255) String city,@NotBlank @Size(max=255) String address) {
    }
    public record ManagerInput(@NotBlank @Size(max=255) String name,@NotBlank @Email @Size(max=255) String email,@NotBlank @Pattern(regexp="[0-9]{10}",message="Mobile number must contain exactly 10 digits.") String mobileNo,@NotBlank @Size(max=255) String address,@NotNull UUID storeId,@NotBlank @Size(min=12,max=72) String password) {
    }
}
