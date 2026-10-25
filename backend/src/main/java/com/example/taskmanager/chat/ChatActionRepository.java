package com.example.taskmanager.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatActionRepository extends JpaRepository<ChatAction, UUID> {
	List<ChatAction> findAllByMessageIdOrderByCreatedAtAsc(Long messageId);

	void deleteAllByConversationId(UUID conversationId);
}
