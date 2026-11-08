package com.markethub.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "markethub.outbox.worker",
        name = "enabled",
        havingValue = "true")
public class TransactionalOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxWorker.class);

    private final TransactionalOutboxService outbox;
    private final Map<String, OutboxEventHandler> handlers;
    private final int batchSize;
    private final Duration leaseDuration;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public TransactionalOutboxWorker(
            TransactionalOutboxService outbox,
            List<OutboxEventHandler> eventHandlers,
            @Value("${markethub.outbox.worker.batch-size:20}") int batchSize,
            @Value("${markethub.outbox.worker.lease-duration:PT2M}") Duration leaseDuration,
            @Value("${markethub.outbox.worker.base-backoff:PT10S}") Duration baseBackoff,
            @Value("${markethub.outbox.worker.max-backoff:PT15M}") Duration maxBackoff) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("outbox batch-size must be between 1 and 100");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || leaseDuration.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("outbox lease-duration must be positive and at most one hour");
        }
        if (baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero()
                || maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0
                || maxBackoff.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("outbox backoff must be positive and max-backoff at most one day");
        }

        this.outbox = outbox;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;

        Map<String, OutboxEventHandler> registered = new HashMap<>();
        for (OutboxEventHandler handler : eventHandlers) {
            if (handler.eventType() == null || handler.eventType().isBlank()) {
                throw new IllegalArgumentException("outbox handler eventType must not be blank");
            }
            if (registered.putIfAbsent(handler.eventType(), handler) != null) {
                throw new IllegalStateException("Duplicate outbox handler for " + handler.eventType());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    @Scheduled(fixedDelayString = "${markethub.outbox.worker.poll-interval:PT5S}")
    public void poll() {
        // Do not claim events until at least one real handler is installed.
        if (handlers.isEmpty()) {
            log.warn("Outbox worker is enabled but no event handlers are registered; skipping poll");
            return;
        }

        final List<TransactionalOutboxService.OutboxMessage> messages;
        try {
            messages = outbox.claimBatch(batchSize, leaseDuration);
        } catch (RuntimeException exception) {
            log.error("Unable to claim transactional outbox batch", exception);
            return;
        }

        for (TransactionalOutboxService.OutboxMessage message : messages) {
            process(message);
        }
    }

    private void process(TransactionalOutboxService.OutboxMessage message) {
        OutboxEventHandler handler = handlers.get(message.eventType());
        if (handler == null) {
            reschedule(message, "No handler registered for event type " + message.eventType());
            return;
        }

        try {
            handler.handle(message);
            if (!outbox.markDelivered(message.id(), message.leaseToken())) {
                log.warn("Outbox event {} was handled but its lease is no longer current", message.id());
            } else {
                log.info("Delivered outbox event {} ({})", message.id(), message.eventType());
            }
        } catch (Exception exception) {
            String detail = exception.getMessage();
            String error = exception.getClass().getSimpleName()
                    + (detail == null || detail.isBlank() ? "" : ": " + detail);
            reschedule(message, error);
        }
    }

    private void reschedule(TransactionalOutboxService.OutboxMessage message, String error) {
        long exponent = Math.min(Math.max(message.attempts() - 1L, 0L), 20L);
        Duration delay;
        try {
            delay = baseBackoff.multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            delay = maxBackoff;
        }
        if (delay.compareTo(maxBackoff) > 0) {
            delay = maxBackoff;
        }

        if (outbox.reschedule(message.id(), message.leaseToken(), error, delay)) {
            log.warn("Rescheduled outbox event {} ({}) after {}: {}",
                    message.id(), message.eventType(), delay, error);
        } else {
            log.warn("Could not reschedule outbox event {} because its lease is no longer current",
                    message.id());
        }
    }
}
