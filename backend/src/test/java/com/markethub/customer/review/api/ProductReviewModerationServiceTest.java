package com.markethub.customer.review.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductReviewModerationServiceTest {
    @Mock private JdbcTemplate jdbc;
    private ProductReviewModerationService moderation;

    @BeforeEach
    void setUp() { moderation = new ProductReviewModerationService(jdbc); }

    @Test
    void rejectsInvalidPaginationAndRatingBeforeDatabaseAccess() {
        assertThatThrownBy(() -> moderation.list(null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> moderation.list(null, 6, null, 20, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> moderation.list(null, 1, null, 20, -1))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
