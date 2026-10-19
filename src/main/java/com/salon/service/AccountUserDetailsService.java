package com.salon.service;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.*;
import com.salon.repository.AccountRepository;

@Service
@Profile("!legacy")
public class AccountUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts; private final TenantSubscriptions subscriptions;
    public AccountUserDetailsService(AccountRepository accounts, TenantSubscriptions subscriptions) {
        this.accounts=accounts; this.subscriptions=subscriptions;
    }
    @Override public UserDetails loadUserByUsername(String mobile) {
        if(mobile==null||!mobile.matches("[0-9]{10}")) throw new UsernameNotFoundException("Mobile number must contain exactly 10 digits.");
        var rows=accounts.findByMobile(mobile);
        if(rows.size()!=1 || rows.get(0).get("password_hash")==null) throw new UsernameNotFoundException("Account not found");
        var row=rows.get(0);
        UUID userId=(UUID)row.get("id");
        var roles=new java.util.ArrayList<>(accounts.findRoles(userId)); roles.removeIf("ROOT"::equals); if(subscriptions.isRoot(userId)) roles.add("ROOT");
        return User.withUsername(userId.toString()).password(row.get("password_hash").toString())
        .roles(roles.toArray(String[]::new)).disabled(!subscriptions.allowed(userId)).build();
    }
}
