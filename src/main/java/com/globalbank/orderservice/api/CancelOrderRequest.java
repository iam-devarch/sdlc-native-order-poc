package com.globalbank.orderservice.api;

import com.globalbank.orderservice.core.domain.CancellationReasonCode;

public record CancelOrderRequest(CancellationReasonCode reasonCode) {}
