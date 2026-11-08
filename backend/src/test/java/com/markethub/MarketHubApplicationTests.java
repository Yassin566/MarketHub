package com.markethub;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MarketHubApplicationTests {
 @Test void applicationIsConfiguredAsSpringBootApplication(){assertTrue(MarketHubApplication.class.isAnnotationPresent(SpringBootApplication.class));}
}