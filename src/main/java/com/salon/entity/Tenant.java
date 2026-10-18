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
