package com.globalbank.orderservice.core.service;

import com.globalbank.orderservice.core.domain.AuditEvent;

public interface AuditEventPublisher {
    void publish(AuditEvent event);
}
