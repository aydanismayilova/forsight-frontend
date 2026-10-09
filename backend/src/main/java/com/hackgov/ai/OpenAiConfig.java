package com.hackgov.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

	@Bean
	RestClient openAiRestClient(OpenAiProperties props) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(props.timeout());

		RestClient.Builder builder = RestClient.builder()
			.baseUrl(props.baseUrl().toString())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (props.isConfigured() ? props.apiKey() : ""));
		if (props.organization() != null && !props.organization().isBlank()) {
			builder.defaultHeader("OpenAI-Organization", props.organization());
		}
		return builder.build();
	}

}
