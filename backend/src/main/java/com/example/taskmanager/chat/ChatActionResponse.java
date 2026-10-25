package com.example.taskmanager.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatActionResponse(
		UUID id,
		ChatAction.Type type,
		ChatAction.Status status,
		Long taskId,
		String title,
		String description,
		Boolean completed,
		Instant createdAt) {

	public static ChatActionResponse from(ChatAction action) {
		return new ChatActionResponse(
				action.getId(),
				action.getType(),
				action.getStatus(),
				action.getTaskId(),
				action.getTitle(),
				action.getDescription(),
				action.getCompleted(),
				action.getCreatedAt());
	}
}
