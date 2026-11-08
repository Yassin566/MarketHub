package com.markethub.common.outbox;

/**
 * Handles one outbox event type. Implementations must be idempotent because
 * delivery is at-least-once: a process can crash after handling but before ack.
 */
public interface OutboxEventHandler {
    String eventType();

    void handle(TransactionalOutboxService.OutboxMessage message) throws Exception;
}
