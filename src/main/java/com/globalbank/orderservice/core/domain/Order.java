package com.globalbank.orderservice.core.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column
    private Instant cancelledAt;

    @Column
    private String cancelledBy;

    @Column(length = 100)
    private String cancellationReason;

    protected Order() {}

    public Order(String customerId, BigDecimal amount, OrderStatus status) {
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public void cancel(String agentId, String reasonCode) {
        if (this.status == OrderStatus.CANCELLED) {
            throw new OrderNotCancellableException(this.id);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelledBy = agentId;
        this.cancellationReason = reasonCode;
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
}
