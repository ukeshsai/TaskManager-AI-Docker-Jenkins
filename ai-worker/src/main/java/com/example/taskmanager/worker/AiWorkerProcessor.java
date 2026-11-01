package com.example.taskmanager.worker;

import org.springframework.stereotype.Component;

@Component
public class AiWorkerProcessor {

	private final OllamaChatClient ollamaChatClient;

	public AiWorkerProcessor(OllamaChatClient ollamaChatClient) {
		this.ollamaChatClient = ollamaChatClient;
	}

	public ChatWorkerResult process(ChatWorkerJob job) {
		if (job.correlationId() == null || job.replyTo() == null || job.systemPrompt() == null
				|| job.systemPrompt().isBlank() || job.messages() == null || job.messages().isEmpty()
				|| job.responseSchema() == null || job.metadata() == null) {
			throw new IllegalArgumentException("The AI job is missing required prompt context");
		}

		String content = ollamaChatClient.complete(job.systemPrompt(), job.messages(), job.responseSchema());
		return new ChatWorkerResult(
				job.correlationId(),
				true,
				content,
				null,
				job.metadata());
	}
}
