package com.hackgov.ai;

import org.springframework.http.HttpStatus;

/** A failure talking to, or interpreting, the model. Carries the status the API should return. */
public class AiServiceException extends RuntimeException {

	private final HttpStatus status;

	public AiServiceException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public AiServiceException(HttpStatus status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
