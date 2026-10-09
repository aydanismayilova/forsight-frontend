package com.hackgov.risk;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model output. Mirrors {@code prompts/risk-analysis.schema.json} field-for-field; keep the two in sync.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskAnalysis(
		@JsonProperty("risk_level") RiskLevel riskLevel,
		@JsonProperty("summary") String summary,
		@JsonProperty("bottleneck_task") BottleneckTask bottleneckTask,
		@JsonProperty("impact_analysis") List<ImpactedTask> impactAnalysis,
		@JsonProperty("actionable_solution") ActionableSolution actionableSolution,
		@JsonProperty("win_rate_probability") WinRateProbability winRateProbability,
		@JsonProperty("assumptions") List<String> assumptions) {

	public enum RiskLevel {
		@JsonProperty("Low") LOW,
		@JsonProperty("Medium") MEDIUM,
		@JsonProperty("High") HIGH,
		@JsonProperty("Critical") CRITICAL
	}

	public record BottleneckTask(
			@JsonProperty("task_id") String taskId,
			@JsonProperty("task_name") String taskName,
			@JsonProperty("root_cause") String rootCause,
			@JsonProperty("slip_days") int slipDays) {
	}

	public record ImpactedTask(
			@JsonProperty("task_id") String taskId,
			@JsonProperty("task_name") String taskName,
			@JsonProperty("delay_days") int delayDays,
			@JsonProperty("original_end") String originalEnd,
			@JsonProperty("projected_end") String projectedEnd,
			@JsonProperty("on_critical_path") boolean onCriticalPath) {
	}

	public record ActionableSolution(
			@JsonProperty("action") String action,
			@JsonProperty("rationale") String rationale,
			@JsonProperty("steps") List<String> steps,
			@JsonProperty("owner") String owner,
			@JsonProperty("effort") String effort,
			@JsonProperty("days_recovered") int daysRecovered) {
	}

	public record WinRateProbability(
			@JsonProperty("before_mitigation") int beforeMitigation,
			@JsonProperty("after_mitigation") int afterMitigation) {
	}

}
