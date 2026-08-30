package com.globalbank.orderservice.api;

import java.math.BigDecimal;

public record CreateOrderRequest(String customerId, BigDecimal amount) {}
