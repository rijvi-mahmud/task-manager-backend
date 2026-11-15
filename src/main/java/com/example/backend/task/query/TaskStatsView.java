package com.example.backend.task.query;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Board-wide counts, computed in one aggregate query. */
@Schema(name = "TaskStats", description = "Counts across the workspace's tasks, unaffected by list filters")
public record TaskStatsView(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "57") Long total,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "20") Long todo,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "12") Long inProgress,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "25") Long done,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Past due and not done", example = "3") Long overdue) {
}
