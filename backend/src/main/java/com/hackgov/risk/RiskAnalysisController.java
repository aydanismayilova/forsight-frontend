package com.hackgov.risk;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackgov.ai.AiServiceException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/risk")
public class RiskAnalysisController {

	private final RiskAnalysisService service;

	public RiskAnalysisController(RiskAnalysisService service) {
		this.service = service;
	}

	@PostMapping("/analyze")
	public RiskAnalysis analyze(@Valid @RequestBody RiskAnalysisRequest request) {
		return service.analyze(request);
	}

	@ExceptionHandler(AiServiceException.class)
	ProblemDetail handleAiFailure(AiServiceException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setTitle("Risk analysis unavailable");
		return problem;
	}

}
