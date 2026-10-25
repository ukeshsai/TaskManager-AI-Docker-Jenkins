package com.example.taskmanager.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
	List<ChatConversation> findAllByOrderByUpdatedAtDesc();
}
