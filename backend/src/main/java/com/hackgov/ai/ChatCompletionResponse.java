package com.hackgov.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The subset of the Chat Completions response this service reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(String id, String model, List<Choice> choices, Usage usage) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Message(String content, String refusal) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(
			@JsonProperty("prompt_tokens") int promptTokens,
			@JsonProperty("completion_tokens") int completionTokens,
			@JsonProperty("total_tokens") int totalTokens) {
	}

}
