package com.example.taskmanager.chat;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
		Long id,
		String role,
		String content,
		Instant createdAt,
		ChatMessage.ProcessingStatus processingStatus,
		String processingError,
		List<ChatActionResponse> actions) {

	public static ChatMessageResponse from(ChatMessage message, List<ChatAction> actions) {
		return new ChatMessageResponse(
				message.getId(),
				message.getRole(),
				message.getContent(),
				message.getCreatedAt(),
				message.getProcessingStatus(),
				message.getProcessingError(),
				actions.stream().map(ChatActionResponse::from).toList());
	}
}
