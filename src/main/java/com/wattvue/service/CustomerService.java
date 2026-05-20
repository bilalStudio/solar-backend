package com.wattvue.service;

import com.wattvue.dto.CustomerRequest;
import com.wattvue.model.Customer;
import com.wattvue.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> getAllCustomers(String status) {
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            return customerRepository.findByStatus(status);
        }
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
    }

    public Customer createCustomer(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("A customer with this email already exists");
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .systemSizeKw(request.getSystemSizeKw())
                .installationDate(request.getInstallationDate())
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .notes(request.getNotes())
                .build();

        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, CustomerRequest request) {
        Customer customer = getCustomerById(id);

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setCity(request.getCity());
        customer.setSystemSizeKw(request.getSystemSizeKw());
        customer.setInstallationDate(request.getInstallationDate());
        if (request.getStatus() != null) customer.setStatus(request.getStatus());
        customer.setNotes(request.getNotes());

        return customerRepository.save(customer);
    }

    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }

    public List<Customer> searchCustomers(String query) {
        return customerRepository.findByNameContainingIgnoreCase(query);
    }
}
