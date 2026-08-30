package com.globalbank.orderservice.core.domain;

public class PaymentAlreadyCapturedException extends RuntimeException {
    private final Long orderId;

    public PaymentAlreadyCapturedException(Long orderId) {
        super("Payment for order " + orderId + " is already captured; use the refund flow");
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
}
