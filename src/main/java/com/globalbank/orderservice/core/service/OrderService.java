package com.globalbank.orderservice.core.service;

import com.globalbank.orderservice.adapters.persistence.OrderRepository;
import com.globalbank.orderservice.core.domain.Order;
import com.globalbank.orderservice.core.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order create(String customerId, BigDecimal amount) {
        return orderRepository.save(new Order(customerId, amount, OrderStatus.CREATED));
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }
}
