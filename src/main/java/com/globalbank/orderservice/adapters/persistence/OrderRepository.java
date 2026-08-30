package com.globalbank.orderservice.adapters.persistence;

import com.globalbank.orderservice.core.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {}
