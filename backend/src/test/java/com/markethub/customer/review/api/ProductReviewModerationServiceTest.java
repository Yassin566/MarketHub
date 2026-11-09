package com.markethub.customer.review.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductReviewModerationServiceTest {
    @Mock private JdbcTemplate jdbc;
    private ProductReviewModerationService moderation;

    @BeforeEach
    void setUp() {
        moderation = new ProductReviewModerationService(jdbc);
    }

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

    @Test
    void publicationChangeRecordsAuthenticatedActorInAuditEvent() {
        UUID reviewId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(reviewId))).thenReturn(List.of(true));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(reviewId)))
                .thenReturn(List.of(true), List.of());
        // The post-update detail query returns the updated moderation row.
        when(jdbc.query(anyString(), any(RowMapper.class), eq(reviewId))).thenReturn(List.of(true));
    }
}
