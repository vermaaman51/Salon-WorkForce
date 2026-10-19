package com.salon.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "username")
    private String username;

    @Column(name = "country")
    private String country;

    @Column(name="_is_active", nullable=false) private boolean active=true;
    @Column(name="segment_type", nullable=false) private String segmentType="BASE";
    @Column(name="subscription_expires_at") private java.time.OffsetDateTime subscriptionExpiresAt;
    public boolean isActive(){return active;} public void setActive(boolean value){active=value;}
    public String getSegmentType(){return segmentType;} public void setSegmentType(String value){segmentType=value;}
    public java.time.OffsetDateTime getSubscriptionExpiresAt(){return subscriptionExpiresAt;} public void setSubscriptionExpiresAt(java.time.OffsetDateTime value){subscriptionExpiresAt=value;}
    @Column(name="gst_number",length=15) private String gstNumber;
    public String getGstNumber(){return gstNumber;} public void setGstNumber(String value){gstNumber=value;}
    public Tenant() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
