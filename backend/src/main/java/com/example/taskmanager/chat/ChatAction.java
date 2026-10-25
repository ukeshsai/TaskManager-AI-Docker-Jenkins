package com.example.taskmanager.chat;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "chat_actions")
public class ChatAction {

	public enum Type {
		CREATE, UPDATE, DELETE
	}

	public enum Status {
		PENDING, APPLIED, REJECTED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conversation_id", nullable = false)
	private ChatConversation conversation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "message_id", nullable = false)
	private ChatMessage message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Type type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.PENDING;

	private Long taskId;

	@Column(length = 200)
	private String title;

	@Column(length = 2000)
	private String description;

	private Boolean completed;

	@Column(length = 200)
	private String originalTitle;

	@Column(length = 2000)
	private String originalDescription;

	private Boolean originalCompleted;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatAction() {
	}

	public ChatAction(
			ChatConversation conversation,
			ChatMessage message,
			Type type,
			Long taskId,
			String title,
			String description,
			Boolean completed,
			String originalTitle,
			String originalDescription,
			Boolean originalCompleted) {
		this.conversation = conversation;
		this.message = message;
		this.type = type;
		this.taskId = taskId;
		this.title = title;
		this.description = description;
		this.completed = completed;
		this.originalTitle = originalTitle;
		this.originalDescription = originalDescription;
		this.originalCompleted = originalCompleted;
	}

	@PrePersist
	void setCreatedAt() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ChatMessage getMessage() {
		return message;
	}

	public Type getType() {
		return type;
	}

	public Status getStatus() {
		return status;
	}

	public Long getTaskId() {
		return taskId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Boolean getCompleted() {
		return completed;
	}

	public String getOriginalTitle() {
		return originalTitle;
	}

	public String getOriginalDescription() {
		return originalDescription;
	}

	public Boolean getOriginalCompleted() {
		return originalCompleted;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void markApplied() {
		status = Status.APPLIED;
	}

	public void markRejected() {
		status = Status.REJECTED;
	}
}
