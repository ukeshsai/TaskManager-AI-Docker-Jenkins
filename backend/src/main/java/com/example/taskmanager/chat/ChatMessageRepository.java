package com.example.taskmanager.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	Optional<ChatMessage> findByIdAndConversationId(Long id, UUID conversationId);

	List<ChatMessage> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

	List<ChatMessage> findTop12ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

	boolean existsByConversationIdAndProcessingStatus(
			UUID conversationId,
			ChatMessage.ProcessingStatus processingStatus);

	void deleteAllByConversationId(UUID conversationId);
}
