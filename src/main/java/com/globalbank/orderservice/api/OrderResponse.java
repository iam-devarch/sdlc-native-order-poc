package com.globalbank.orderservice.api;

import com.globalbank.orderservice.core.domain.Order;
import com.globalbank.orderservice.core.domain.OrderStatus;

import java.math.BigDecimal;

public record OrderResponse(Long id, String customerId, BigDecimal amount, OrderStatus status) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getAmount(), order.getStatus());
    }
}
