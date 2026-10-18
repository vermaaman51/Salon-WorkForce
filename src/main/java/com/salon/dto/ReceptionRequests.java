package com.salon.dto;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import com.salon.constant.*;

public final class ReceptionRequests {
    private ReceptionRequests() {
    }
    public record NewOrder(@NotNull OrderType orderType,@NotBlank @Size(max=255) String customerName,@NotBlank @Pattern(regexp="[0-9]{10}",message="Mobile number must contain exactly 10 digits.") String customerMobile,@Size(max=2000) String details,@Size(max=255) String service,@DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal amount,LocalDateTime appointmentAt) {
    }
    public record Assignment(@NotNull UUID assignee) {
    }
    public record Checkout(@NotNull PaymentStatus paymentStatus,PaymentMode paymentMode) {
    }
    public record Payment(@NotNull PaymentMode paymentMode) {
    }
}
