package com.salon.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import com.salon.service.AdminAccountService;

@Configuration
@Profile("!legacy")
public class AdminBootstrap {
    @Bean ApplicationRunner adminPassword(AdminAccountService service,@Value("${ADMIN_PASSWORD:}") String password) {
        return args -> service.initializePassword(password);
    }
}
