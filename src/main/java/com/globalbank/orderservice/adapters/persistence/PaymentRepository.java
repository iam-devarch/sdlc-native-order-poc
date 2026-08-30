package com.globalbank.orderservice.adapters.persistence;

import com.globalbank.orderservice.core.domain.Payment;
import com.globalbank.orderservice.core.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
