package com.hackgov.risk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.hackgov.ai.AiServiceException;
import com.hackgov.ai.ChatCompletionResponse;
import com.hackgov.ai.OpenAiProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RiskAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);

	private final RestClient openAi;
	private final OpenAiProperties props;
	private final JsonMapper json;
	private final String systemPrompt;
	private final Map<String, Object> responseSchema;

	public RiskAnalysisService(RestClient openAiRestClient, OpenAiProperties props, JsonMapper json,
			@Value("classpath:prompts/risk-analyst.system.md") Resource systemPrompt,
			@Value("classpath:prompts/risk-analysis.schema.json") Resource responseSchema) throws IOException {
		this.openAi = openAiRestClient;
		this.props = props;
		this.json = json;
		try (InputStream prompt = systemPrompt.getInputStream(); InputStream schema = responseSchema.getInputStream()) {
			this.systemPrompt = new String(prompt.readAllBytes(), StandardCharsets.UTF_8);
			this.responseSchema = json.readValue(schema, new TypeReference<Map<String, Object>>() {});
		}
	}

	public RiskAnalysis analyze(RiskAnalysisRequest request) {
		validatePlan(request);
		if (!props.isConfigured()) {
			throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "OPENAI_API_KEY is not configured");
		}

		ChatCompletionResponse response = callWithRetry(buildChatRequest(request));
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			throw new AiServiceException(HttpStatus.BAD_GATEWAY, "Model returned no choices");
		}
		if (response.usage() != null) {
			log.info("Risk analysis for '{}' used {} prompt + {} completion tokens ({})", request.projectName(),
					response.usage().promptTokens(), response.usage().completionTokens(), response.model());
		}

		ChatCompletionResponse.Choice choice = response.choices().getFirst();
		if (choice.message() == null) {
			throw new AiServiceException(HttpStatus.BAD_GATEWAY, "Model returned an empty message");
		}
		if (choice.message().refusal() != null) {
			throw new AiServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "Model refused: " + choice.message().refusal());
		}
		if ("length".equals(choice.finishReason())) {
			throw new AiServiceException(HttpStatus.BAD_GATEWAY,
					"Model output was truncated; raise hackgov.ai.openai.max-completion-tokens");
		}

		RiskAnalysis analysis;
		try {
			analysis = json.readValue(choice.message().content(), RiskAnalysis.class);
		}
		catch (JacksonException ex) {
			throw new AiServiceException(HttpStatus.BAD_GATEWAY, "Model returned malformed JSON", ex);
		}
		validateAnalysis(analysis, request);
		return analysis;
	}

	/* ───────────────────────── Request building ───────────────────────── */

	private Map<String, Object> buildChatRequest(RiskAnalysisRequest request) {
		String projectData = json.writeValueAsString(toModelInput(request));
		String userMessage = "<project_data>\n" + projectData + "\n</project_data>";

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", props.model());
		body.put("messages", List.of(
				// o-series models take instructions through the "developer" role.
				Map.of("role", props.isReasoningModel() ? "developer" : "system", "content", systemPrompt),
				Map.of("role", "user", "content", userMessage)));
		body.put("response_format", Map.of("type", "json_schema", "json_schema", responseSchema));
		body.put("max_completion_tokens", props.maxCompletionTokens());
		if (props.isReasoningModel()) {
			body.put("reasoning_effort", props.reasoningEffort());
		}
		else {
			body.put("temperature", props.temperature());
		}
		return body;
	}

	/** Re-keys the request into the snake_case shape the system prompt documents. */
	private Map<String, Object> toModelInput(RiskAnalysisRequest r) {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("project", r.projectName());
		input.put("today", (r.today() != null ? r.today() : LocalDate.now()).toString());
		input.put("deadline", r.deadline().toString());
		input.put("tasks", r.tasks().stream().map(t -> {
			Map<String, Object> task = new LinkedHashMap<>();
			task.put("id", t.id());
			task.put("name", t.name());
			task.put("owner", t.owner());
			task.put("start", t.start().toString());
			task.put("end", t.end().toString());
			task.put("depends_on", t.dependsOn() != null ? t.dependsOn() : List.of());
			task.put("progress", t.progress());
			task.put("status", t.status());
			return task;
		}).toList());
		if (r.scenario() != null) {
			Map<String, Object> scenario = new LinkedHashMap<>();
			scenario.put("deadline_shift_days", r.scenario().deadlineShiftDays());
			scenario.put("task_delays", r.scenario().taskDelays());
			scenario.put("unavailable_members", r.scenario().unavailableMembers());
			scenario.put("notes", r.scenario().notes());
			input.put("scenario", scenario);
		}
		if (r.question() != null && !r.question().isBlank()) {
			input.put("question", r.question());
		}
		return input;
	}

	/* ───────────────────────── Transport ───────────────────────── */

	private ChatCompletionResponse callWithRetry(Map<String, Object> body) {
		for (int attempt = 0;; attempt++) {
			try {
				return openAi.post().uri("/chat/completions").body(body).retrieve().body(ChatCompletionResponse.class);
			}
			catch (RestClientResponseException ex) {
				HttpStatusCode status = ex.getStatusCode();
				boolean retryable = status.value() == 429 || status.is5xxServerError();
				if (!retryable || attempt >= props.maxRetries()) {
					log.warn("OpenAI call failed with {}: {}", status, ex.getResponseBodyAsString());
					throw new AiServiceException(status.value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
							"OpenAI request failed with status " + status.value(), ex);
				}
				backoff(attempt, ex.getResponseHeaders() != null ? ex.getResponseHeaders().getFirst("retry-after") : null);
			}
			catch (ResourceAccessException ex) {
				if (attempt >= props.maxRetries()) {
					throw new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "OpenAI is unreachable or timed out", ex);
				}
				backoff(attempt, null);
			}
		}
	}

	private static void backoff(int attempt, String retryAfter) {
		long delayMs;
		try {
			delayMs = retryAfter != null ? (long) (Double.parseDouble(retryAfter) * 1000) : -1;
		}
		catch (NumberFormatException ex) {
			delayMs = -1;
		}
		if (delayMs < 0) {
			delayMs = (1000L << attempt) + ThreadLocalRandom.current().nextLong(250);
		}
		try {
			Thread.sleep(Math.min(delayMs, 20_000));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while retrying", ex);
		}
	}

	/* ───────────────────────── Validation ───────────────────────── */

	/** Rejects plans the model cannot reason about reliably before spending tokens on them. */
	private static void validatePlan(RiskAnalysisRequest r) {
		Map<String, List<String>> graph = new HashMap<>();
		for (RiskAnalysisRequest.Task t : r.tasks()) {
			if (graph.put(t.id(), t.dependsOn() != null ? t.dependsOn() : List.of()) != null) {
				throw badRequest("Duplicate task id: " + t.id());
			}
			if (t.end().isBefore(t.start())) {
				throw badRequest("Task " + t.id() + " ends before it starts");
			}
		}
		graph.forEach((id, deps) -> deps.forEach(dep -> {
			if (!graph.containsKey(dep)) {
				throw badRequest("Task " + id + " depends on unknown task " + dep);
			}
		}));
		Set<String> done = new HashSet<>();
		for (String id : graph.keySet()) {
			detectCycle(id, graph, new HashSet<>(), done);
		}
	}

	private static void detectCycle(String id, Map<String, List<String>> graph, Set<String> path, Set<String> done) {
		if (done.contains(id)) {
			return;
		}
		if (!path.add(id)) {
			throw badRequest("Dependency cycle involving task " + id);
		}
		for (String dep : graph.get(id)) {
			detectCycle(dep, graph, path, done);
		}
		path.remove(id);
		done.add(id);
	}

	/** Structured Outputs guarantees the shape; this checks the values against the actual plan. */
	private static void validateAnalysis(RiskAnalysis a, RiskAnalysisRequest r) {
		Set<String> taskIds = new HashSet<>();
		r.tasks().forEach(t -> taskIds.add(t.id()));

		if (a.riskLevel() == null || a.bottleneckTask() == null || a.actionableSolution() == null
				|| a.winRateProbability() == null || a.impactAnalysis() == null) {
			throw invalid("missing required fields");
		}
		if (!taskIds.contains(a.bottleneckTask().taskId())) {
			throw invalid("unknown bottleneck task " + a.bottleneckTask().taskId());
		}
		for (RiskAnalysis.ImpactedTask impacted : a.impactAnalysis()) {
			if (!taskIds.contains(impacted.taskId())) {
				throw invalid("unknown impacted task " + impacted.taskId());
			}
			try {
				LocalDate.parse(impacted.originalEnd());
				LocalDate.parse(impacted.projectedEnd());
			}
			catch (DateTimeParseException ex) {
				throw invalid("bad date on task " + impacted.taskId());
			}
		}
		int before = a.winRateProbability().beforeMitigation();
		int after = a.winRateProbability().afterMitigation();
		if (before < 0 || before > 100 || after < 0 || after > 100) {
			throw invalid("probability out of range");
		}
	}

	private static AiServiceException invalid(String reason) {
		return new AiServiceException(HttpStatus.BAD_GATEWAY, "Model output failed validation: " + reason);
	}

	private static ResponseStatusException badRequest(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}

}
