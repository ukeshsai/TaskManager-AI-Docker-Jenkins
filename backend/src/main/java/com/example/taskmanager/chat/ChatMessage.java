package com.example.taskmanager.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private ChatConversation conversation;

	@Column(nullable = false, length = 20)
	private String role;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ProcessingStatus processingStatus;

	@Column(columnDefinition = "text")
	private String processingError;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatMessage() {
	}

	public ChatMessage(ChatConversation conversation, String role, String content) {
		this.conversation = conversation;
		this.role = role;
		this.content = content;
	}

	@PrePersist
	void setCreatedAt() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public ChatConversation getConversation() {
		return conversation;
	}

	public String getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public ProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public String getProcessingError() {
		return processingError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void markProcessing() {
		processingStatus = ProcessingStatus.PROCESSING;
		processingError = null;
	}

	public void markCompleted() {
		processingStatus = ProcessingStatus.COMPLETED;
		processingError = null;
	}

	public void markFailed(String error) {
		processingStatus = ProcessingStatus.FAILED;
		processingError = error;
	}

	public enum ProcessingStatus {
		PROCESSING,
		COMPLETED,
		FAILED
	}
}
