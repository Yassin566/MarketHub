package com.markethub.customer.review.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {
    @Mock private JdbcTemplate jdbc;
    private ProductReviewService reviews;

    @BeforeEach
    void setUp() {
        reviews = new ProductReviewService(jdbc);
    }

    @Test
    void createRejectsRatingsOutsideOneToFiveBeforeDatabaseAccess() {
        assertThatThrownBy(() -> reviews.create(UUID.randomUUID(), UUID.randomUUID(), 0, "Review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
        assertThatThrownBy(() -> reviews.create(UUID.randomUUID(), UUID.randomUUID(), 6, "Review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
        verifyNoInteractions(jdbc);
    }

    @Test
    void createRejectsReviewTextLongerThanTwoThousandCharacters() {
        assertThatThrownBy(() -> reviews.create(UUID.randomUUID(), UUID.randomUUID(), 5, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
        verifyNoInteractions(jdbc);
    }

    @Test
    void updateRejectsInvalidRatingBeforeDatabaseAccess() {
        assertThatThrownBy(() -> reviews.update(UUID.randomUUID(), UUID.randomUUID(), -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void publicReviewListingRejectsInvalidPaginationBeforeDatabaseAccess() {
        assertThatThrownBy(() -> reviews.listPublished(UUID.randomUUID(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviews.listPublished(UUID.randomUUID(), 101, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reviews.listPublished(UUID.randomUUID(), 20, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
