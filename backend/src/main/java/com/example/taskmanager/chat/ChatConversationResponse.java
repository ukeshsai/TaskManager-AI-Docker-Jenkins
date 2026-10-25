package com.example.taskmanager.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatConversationResponse(UUID id, String title, Instant createdAt, Instant updatedAt) {
	public static ChatConversationResponse from(ChatConversation conversation) {
		return new ChatConversationResponse(
				conversation.getId(),
				conversation.getTitle(),
				conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}
}
