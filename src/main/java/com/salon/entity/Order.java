package com.salon.entity;

import jakarta.persistence.*;
import com.salon.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="orders")
public class Order {
public enum OrderStatus { PENDING, ASSIGNED, COMPLETED }
@Id @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;

    @Column(name="tenant_id", nullable=false)
    private UUID tenantId;

    @Column(name="store_id", nullable=false)
    private UUID storeId;

    @Column(name="assignee", nullable=true)
    private UUID assignee;

    @Enumerated(EnumType.STRING) @Column(name="order_type",nullable=false,length=20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING) @Column(name="order_status",nullable=false,length=20)
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING) @Column(name="payment_status",nullable=false,length=20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    @Column(name="payment_completed_at",insertable=false,updatable=false)
    private java.time.OffsetDateTime paymentCompletedAt;
    public java.time.OffsetDateTime getPaymentCompletedAt(){return paymentCompletedAt;}

    @Enumerated(EnumType.STRING) @Column(name="payment_mode",length=10)
    private PaymentMode paymentMode;

    @Column(name="customer_name")
    private String customerName;

    @Column(name="customer_mobile",nullable=false,length=16)
    private String customerMobile;

    @Column(name="details",nullable=false,length=2000)
    private String details;

    @Column(name="service",length=255)
    private String service;

    @Column(name="amount",nullable=false,precision=12,scale=2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name="appointment_at")
    private LocalDateTime appointmentAt;

    @Column(name="created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name="modified_at",insertable=false,updatable=false) private java.time.OffsetDateTime modifiedAt;
    public java.time.OffsetDateTime getModifiedAt(){return modifiedAt;}
    public Order(){}
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public UUID getAssignee() { return assignee; }
    public void setAssignee(UUID assignee) { this.assignee = assignee; }
    public OrderType getOrderType(){return orderType;}
    public void setOrderType(OrderType orderType){this.orderType=orderType;}
    public OrderStatus getOrderStatus(){return orderStatus;}
    public void setOrderStatus(OrderStatus orderStatus){this.orderStatus=orderStatus;}
    public PaymentStatus getPaymentStatus(){return paymentStatus;}
    public void setPaymentStatus(PaymentStatus paymentStatus){this.paymentStatus=paymentStatus;}
    public PaymentMode getPaymentMode(){return paymentMode;}
    public void setPaymentMode(PaymentMode paymentMode){this.paymentMode=paymentMode;}
    public String getCustomerName(){return customerName;}
    public void setCustomerName(String customerName){this.customerName=customerName;}
    public String getCustomerMobile(){return customerMobile;}
    public void setCustomerMobile(String customerMobile){this.customerMobile=customerMobile;}
    public String getDetails(){return details;}
    public void setDetails(String details){this.details=details;}
    public String getService(){return service;}
    public void setService(String service){this.service=service;}
    public BigDecimal getAmount(){return amount;}
    public void setAmount(BigDecimal amount){this.amount=amount;}
    public LocalDateTime getAppointmentAt(){return appointmentAt;}
    public void setAppointmentAt(LocalDateTime appointmentAt){this.appointmentAt=appointmentAt;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
