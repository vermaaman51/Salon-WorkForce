package com.salon.service;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.salon.repository.AccountRepository;
import com.salon.utility.PasswordPolicy;

@Service
public class AdminAccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    public AdminAccountService(AccountRepository accounts,PasswordEncoder passwords) {
        this.accounts=accounts;
        this.passwords=passwords;
    }
    public void initializePassword(String password) {
        if(password.length() < PasswordPolicy.MIN_CHARACTERS) return;
        accounts.initializeAdminPassword(passwords.encode(password));
    }
}
