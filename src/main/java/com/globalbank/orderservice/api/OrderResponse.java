package com.globalbank.orderservice.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.globalbank.orderservice.core.domain.Order;
import com.globalbank.orderservice.core.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long id,
        String customerId,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
        OrderStatus status,
        Instant cancelledAt,
        String cancelledBy,
        String cancellationReason) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getStatus(),
                order.getCancelledAt(),
                order.getCancelledBy(),
                order.getCancellationReason());
    }
}
