package com.example.taskmanager.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OllamaChatClient {

	private final RestClient restClient;
	private final String model;
	private final CurrentDateTimeContext dateTimeContext;

	public OllamaChatClient(
			@Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
			@Value("${ollama.model:qwen2.5:1.5b}") String model,
			CurrentDateTimeContext dateTimeContext) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
		this.model = model;
		this.dateTimeContext = dateTimeContext;
	}

	public String complete(
			String systemPrompt,
			List<ChatPromptMessage> promptMessages,
			Map<String, Object> responseSchema) {
		List<OllamaMessage> messages = new ArrayList<>();
			messages.add(new OllamaMessage(
					"system",
					systemPrompt + "\n\n" + dateTimeContext.systemPromptContext()));
		promptMessages.forEach(message -> messages.add(new OllamaMessage(message.role(), message.content())));

		OllamaResponse response;
		try {
			response = restClient.post()
					.uri("/api/chat")
					.body(new OllamaRequest(model, messages, responseSchema, false))
					.retrieve()
					.body(OllamaResponse.class);
		} catch (RestClientException exception) {
			throw new IllegalStateException(
					"The local AI model is not ready. Check that Ollama is running and the model is downloaded.",
					exception);
		}

		if (response == null || response.message() == null || response.message().content() == null
				|| response.message().content().isBlank()) {
			throw new IllegalStateException("The local AI model returned an empty response");
		}
		return response.message().content();
	}

	private record OllamaRequest(String model, List<OllamaMessage> messages, Map<String, Object> format, boolean stream) {
	}

	private record OllamaMessage(String role, String content) {
	}

	private record OllamaResponse(OllamaMessage message) {
	}
}
