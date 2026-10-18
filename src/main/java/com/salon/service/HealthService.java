package com.salon.service;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import com.salon.repository.HealthRepository;

@Service
public class HealthService {
    private final HealthRepository repository;
    public HealthService(HealthRepository repository) {
        this.repository=repository;
    }
    public boolean isHealthy() {
        try {
            repository.checkConnection();
            return true;
        }
        catch(DataAccessException exception) {
            return false;
        }
    }
}
