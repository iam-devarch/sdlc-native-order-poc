package com.globalbank.orderservice.adapters.audit;

import com.globalbank.orderservice.core.domain.AuditEvent;
import com.globalbank.orderservice.core.service.AuditEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAuditEventPublisher implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuditEventPublisher.class);

    @Override
    public void publish(AuditEvent event) {
        log.info("AUDIT actor={} action={} entity={} timestamp={}",
                 event.actor(), event.action(), event.entity(), event.timestamp());
    }
}
