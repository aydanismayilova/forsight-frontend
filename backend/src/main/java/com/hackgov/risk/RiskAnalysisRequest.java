package com.hackgov.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Input from the dashboard: the current plan plus an optional what-if scenario and question. */
public record RiskAnalysisRequest(
		@NotBlank @Size(max = 200) String projectName,
		@NotNull LocalDate deadline,
		LocalDate today,
		@NotEmpty @Size(max = 200) List<@Valid @NotNull Task> tasks,
		@Valid Scenario scenario,
		@Size(max = 1000) String question) {

	public record Task(
			@NotBlank @Size(max = 64) String id,
			@NotBlank @Size(max = 200) String name,
			@Size(max = 120) String owner,
			@NotNull LocalDate start,
			@NotNull LocalDate end,
			List<@NotBlank String> dependsOn,
			@Min(0) @Max(100) Integer progress,
			@Size(max = 40) String status) {
	}

	public record Scenario(
			@Min(-60) @Max(60) Integer deadlineShiftDays,
			Map<String, @Min(0) @Max(120) Integer> taskDelays,
			List<@NotBlank String> unavailableMembers,
			@Size(max = 1000) String notes) {
	}

}
