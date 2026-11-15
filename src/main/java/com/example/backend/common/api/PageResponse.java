package com.example.backend.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Stable JSON envelope for one page of results. Kept separate from Spring's
 * {@link Page} so the public contract doesn't change with framework internals.
 */
@Schema(description = "One page of results")
public record PageResponse<T>(
        @Schema(requiredMode = RequiredMode.REQUIRED) List<T> items,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Zero-based page index", example = "0") int page,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Requested page size", example = "25") int size,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Matching items across all pages", example = "57") long totalItems,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "3") int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
