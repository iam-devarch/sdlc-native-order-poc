package com.globalbank.orderservice.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void cancel_transitionsCreatedToCancelled() {
        Order order = new Order("CUST-001", BigDecimal.TEN, OrderStatus.CREATED);

        order.cancel("agent-007", "CUSTOMER_REQUEST");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo("agent-007");
        assertThat(order.getCancellationReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void cancel_onAlreadyCancelledOrder_throws() {
        Order order = new Order("CUST-001", BigDecimal.TEN, OrderStatus.CREATED);
        order.cancel("agent-007", "CUSTOMER_REQUEST");

        assertThatThrownBy(() -> order.cancel("agent-007", "CUSTOMER_REQUEST"))
                .isInstanceOf(OrderNotCancellableException.class);
    }
}
