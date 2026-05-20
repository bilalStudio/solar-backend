package com.wattvue.config;

import com.wattvue.model.Customer;
import com.wattvue.model.User;
import com.wattvue.repository.CustomerRepository;
import com.wattvue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .name("WattVue Admin")
                    .email("admin@wattvue.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .authProvider("LOCAL")
                    .isActive(true)
                    .build();

            userRepository.save(admin);

            log.info("Default admin created: admin@wattvue.com / admin123");
        }

        if (customerRepository.count() == 0) {
            customerRepository.save(Customer.builder()
                    .name("Ali Hassan")
                    .email("ali.demo@example.com")
                    .phone("+92 300 1234567")
                    .city("Lahore")
                    .address("DHA Phase 5")
                    .systemSizeKw(10.0)
                    .installationDate("2023-03-15")
                    .status("active")
                    .notes("Demo customer for testing")
                    .build());

            log.info("Demo customer created");
        }
    }
}
