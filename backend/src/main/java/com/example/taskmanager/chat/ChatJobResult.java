package com.example.taskmanager.chat;

import java.util.Map;
import java.util.UUID;

public record ChatJobResult(
		UUID correlationId,
		boolean successful,
		String content,
		String error,
		Map<String, String> metadata) {
}
