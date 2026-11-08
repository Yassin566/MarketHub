package com.markethub.customer.payment.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markethub.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks/payments")
public class SandboxPaymentWebhookController {
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private final SandboxPaymentWebhookService webhooks;
    private final String secret;
    private final ObjectMapper mapper = new ObjectMapper();

    public SandboxPaymentWebhookController(
            SandboxPaymentWebhookService webhooks,
            @Value("${markethub.payment.webhook.secret:}") String secret) {
        this.webhooks = webhooks;
        this.secret = secret == null ? "" : secret;
    }

    @PostMapping("/sandbox")
    public ApiResponse<SandboxPaymentWebhookService.WebhookResult> receive(
            @RequestHeader("X-MarketHub-Event-Id") String eventId,
            @RequestHeader("X-MarketHub-Timestamp") String timestamp,
            @RequestHeader("X-MarketHub-Signature") String signature,
            @RequestBody String rawBody) {
        if (secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Sandbox webhook endpoint is not configured");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook timestamp");
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > MAX_CLOCK_SKEW_SECONDS) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook timestamp is outside the allowed window");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event ID");
        }
        verifySignature(timestamp, rawBody, signature);

        try {
            JsonNode payload = mapper.readTree(rawBody);
            String eventType = requiredText(payload, "type");
            String providerReference = requiredText(payload, "providerReference");
            String amount = requiredText(payload, "amount");
            String currency = requiredText(payload, "currency");
            if (!"payment.succeeded".equals(eventType) && !"payment.failed".equals(eventType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment event type");
            }
            return ApiResponse.ok(webhooks.process(
                    eventId, eventType, providerReference, amount, currency, rawBody));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment webhook payload");
        }
    }

    private void verifySignature(String timestamp, String body, String supplied) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            if (supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify webhook signature", exception);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value.asText();
    }
}
