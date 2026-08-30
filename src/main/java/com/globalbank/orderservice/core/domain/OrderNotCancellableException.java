package com.globalbank.orderservice.core.domain;

public class OrderNotCancellableException extends RuntimeException {
    private final Long orderId;

    public OrderNotCancellableException(Long id) {
        super("Order " + id + " is already cancelled");
        this.orderId = id;
    }

    public Long getOrderId() { return orderId; }
}
