package com.markethub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MarketHubApplicationTests {

    @Test
    void applicationContextStartsAndApplicationIsConfigured() {
        assertTrue(MarketHubApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }
}
