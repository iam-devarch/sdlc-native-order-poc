package com.globalbank.orderservice.core.service;

import com.globalbank.orderservice.adapters.persistence.OrderRepository;
import com.globalbank.orderservice.adapters.persistence.PaymentRepository;
import com.globalbank.orderservice.core.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock AuditEventPublisher auditEventPublisher;
    @InjectMocks OrderService orderService;

    @Test
    void cancel_happyPath_savesOrderAndPublishesAuditEvent() {
        Order order = new Order("CUST-001", BigDecimal.TEN, OrderStatus.CREATED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(42L, PaymentStatus.CAPTURED)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancel(42L, "agent-007", CancellationReasonCode.CUSTOMER_REQUEST);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancelledBy()).isEqualTo("agent-007");
        assertThat(result.getCancellationReason()).isEqualTo("CUSTOMER_REQUEST");
        assertThat(result.getCancelledAt()).isNotNull();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.actor()).isEqualTo("agent-007");
        assertThat(event.action()).isEqualTo("ORDER_CANCELLED");
        assertThat(event.entity()).isEqualTo("order:42");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void cancel_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(99L, "agent-007", CancellationReasonCode.CUSTOMER_REQUEST))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void cancel_paymentCaptured_throws() {
        Order order = new Order("CUST-001", BigDecimal.TEN, OrderStatus.CREATED);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(42L, PaymentStatus.CAPTURED)).thenReturn(true);

        assertThatThrownBy(() -> orderService.cancel(42L, "agent-007", CancellationReasonCode.CUSTOMER_REQUEST))
                .isInstanceOf(PaymentAlreadyCapturedException.class);

        verify(orderRepository, never()).save(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void cancel_alreadyCancelled_throws() {
        Order order = new Order("CUST-001", BigDecimal.TEN, OrderStatus.CREATED);
        order.cancel("agent-007", "CUSTOMER_REQUEST");
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(42L, PaymentStatus.CAPTURED)).thenReturn(false);

        assertThatThrownBy(() -> orderService.cancel(42L, "agent-007", CancellationReasonCode.CUSTOMER_REQUEST))
                .isInstanceOf(OrderNotCancellableException.class);

        verify(orderRepository, never()).save(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void cancel_nullReasonCode_throws() {
        assertThatThrownBy(() -> orderService.cancel(42L, "agent-007", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode is required");
    }
}
