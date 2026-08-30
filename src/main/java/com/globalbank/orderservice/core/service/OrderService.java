package com.globalbank.orderservice.core.service;

import com.globalbank.orderservice.adapters.persistence.OrderRepository;
import com.globalbank.orderservice.adapters.persistence.PaymentRepository;
import com.globalbank.orderservice.core.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditEventPublisher auditEventPublisher;

    public OrderService(OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        AuditEventPublisher auditEventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public Order create(String customerId, BigDecimal amount) {
        return orderRepository.save(new Order(customerId, amount, OrderStatus.CREATED));
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order cancel(Long orderId, String agentId, CancellationReasonCode reasonCode) {
        if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.CAPTURED)) {
            throw new PaymentAlreadyCapturedException(orderId);
        }

        order.cancel(agentId, reasonCode.name());
        Order saved = orderRepository.save(order);

        auditEventPublisher.publish(AuditEvent.of(
                agentId,
                "ORDER_CANCELLED",
                "order:" + orderId,
                Instant.now()));

        return saved;
    }
}
