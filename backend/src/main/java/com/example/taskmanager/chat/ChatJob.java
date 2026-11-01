package com.example.taskmanager.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatJob(
		UUID correlationId,
		int attempt,
		String replyTo,
		String systemPrompt,
		List<ChatPromptMessage> messages,
		Map<String, Object> responseSchema,
		Map<String, String> metadata) {
}
