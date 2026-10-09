package com.hackgov.ai;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * OpenAI connection settings, bound from {@code hackgov.ai.openai.*}.
 *
 * <p>The API key is deliberately not validated at startup so the app (and its tests) can run
 * without it; calls fail with 503 until it is set.
 */
@ConfigurationProperties(prefix = "hackgov.ai.openai")
public record OpenAiProperties(
		String apiKey,
		@DefaultValue("https://api.openai.com/v1") URI baseUrl,
		@DefaultValue("gpt-4o") String model,
		@DefaultValue("medium") String reasoningEffort,
		@DefaultValue("0.2") double temperature,
		@DefaultValue("4000") int maxCompletionTokens,
		@DefaultValue("60s") Duration timeout,
		@DefaultValue("2") int maxRetries,
		String organization) {

	public boolean isConfigured() {
		return StringUtils.hasText(apiKey);
	}

	/** o-series models (o1, o3, o3-mini, o4-mini…) reject temperature and take reasoning_effort instead. */
	public boolean isReasoningModel() {
		return model.matches("^o\\d.*");
	}

}
