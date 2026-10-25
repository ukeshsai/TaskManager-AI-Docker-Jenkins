package com.example.taskmanager.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

	List<ChatMessage> findTop12ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

	void deleteAllByConversationId(UUID conversationId);
}
