package com.example.taskmanager.worker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatWorkerJob(
		UUID correlationId,
		int attempt,
		String replyTo,
		String systemPrompt,
		List<ChatPromptMessage> messages,
		Map<String, Object> responseSchema,
		Map<String, String> metadata) {
}
