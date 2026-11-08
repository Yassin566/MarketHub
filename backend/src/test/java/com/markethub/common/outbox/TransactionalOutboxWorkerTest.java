package com.markethub.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalOutboxWorkerTest {
    @Mock private TransactionalOutboxService outbox;
    @Mock private OutboxEventHandler handler;

    private final UUID eventId = UUID.randomUUID();
    private final UUID leaseToken = UUID.randomUUID();

    private TransactionalOutboxService.OutboxMessage message;

    @BeforeEach
    void setUp() {
        message = new TransactionalOutboxService.OutboxMessage(
                eventId, "TEST_EVENT", "{\"id\":\"test\"}", 1, leaseToken);
    }

    @Test
    void doesNotClaimEventsWhenNoHandlersAreRegistered() {
        var worker = worker(List.of());

        worker.poll();

        verifyNoInteractions(outbox);
    }

    @Test
    void invokesHandlerAndAcknowledgesOnlyWithCurrentLease() throws Exception {
        when(handler.eventType()).thenReturn("TEST_EVENT");
        when(outbox.claimBatch(20, Duration.ofMinutes(2))).thenReturn(List.of(message));
        when(outbox.markDelivered(eventId, leaseToken)).thenReturn(true);
        var worker = worker(List.of(handler));

        worker.poll();

        verify(handler).handle(message);
        verify(outbox).markDelivered(eventId, leaseToken);
        verify(outbox, never()).reschedule(any(), any(), anyString(), any());
    }

    @Test
    void schedulesRetryWhenHandlerFailsUsingAttemptBasedBackoff() throws Exception {
        when(handler.eventType()).thenReturn("TEST_EVENT");
        var secondAttempt = new TransactionalOutboxService.OutboxMessage(
                eventId, "TEST_EVENT", "{}", 2, leaseToken);
        when(outbox.claimBatch(20, Duration.ofMinutes(2))).thenReturn(List.of(secondAttempt));
        doThrow(new IllegalStateException("temporary failure")).when(handler).handle(secondAttempt);
        when(outbox.reschedule(eventId, leaseToken, "IllegalStateException: temporary failure",
                Duration.ofSeconds(20))).thenReturn(true);
        var worker = worker(List.of(handler));

        worker.poll();

        verify(outbox).reschedule(eventId, leaseToken,
                "IllegalStateException: temporary failure", Duration.ofSeconds(20));
        verify(outbox, never()).markDelivered(any(), any());
    }

    @Test
    void reschedulesEventsWithoutARegisteredHandlerInsteadOfAcknowledgingThem() {
        when(handler.eventType()).thenReturn("OTHER_EVENT");
        when(outbox.claimBatch(20, Duration.ofMinutes(2))).thenReturn(List.of(message));
        when(outbox.reschedule(eventId, leaseToken,
                "No handler registered for event type TEST_EVENT", Duration.ofSeconds(10)))
                .thenReturn(true);
        var worker = worker(List.of(handler));

        worker.poll();

        verify(outbox).reschedule(eventId, leaseToken,
                "No handler registered for event type TEST_EVENT", Duration.ofSeconds(10));
        verify(outbox, never()).markDelivered(any(), any());
        verify(handler, never()).handle(any());
    }

    private TransactionalOutboxWorker worker(List<OutboxEventHandler> handlers) {
        return new TransactionalOutboxWorker(outbox, handlers, 20,
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(15));
    }
}
