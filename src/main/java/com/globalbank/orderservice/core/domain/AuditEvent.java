package com.globalbank.orderservice.core.domain;

import java.time.Instant;

public record AuditEvent(String actor, String action, String entity, Instant timestamp) {
    public static AuditEvent of(String actor, String action, String entity, Instant timestamp) {
        return new AuditEvent(actor, action, entity, timestamp);
    }
}
