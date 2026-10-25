package com.example.taskmanager.chat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;
	private final ChatActionService actionService;

	public ChatController(ChatService chatService, ChatActionService actionService) {
		this.chatService = chatService;
		this.actionService = actionService;
	}

	@GetMapping("/conversations")
	public List<ChatConversationResponse> listConversations() {
		return chatService.listConversations();
	}

	@PostMapping("/conversations")
	public ChatConversationResponse createConversation() {
		return chatService.createConversation();
	}

	@DeleteMapping("/conversations/{conversationId}")
	public ResponseEntity<Void> deleteConversation(@PathVariable UUID conversationId) {
		chatService.deleteConversation(conversationId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/conversations/{conversationId}/messages")
	public List<ChatMessageResponse> listMessages(@PathVariable UUID conversationId) {
		return chatService.listMessages(conversationId);
	}

	@PostMapping("/conversations/{conversationId}/messages")
	public ChatMessageResponse sendMessage(
			@PathVariable UUID conversationId,
			@Valid @RequestBody ChatRequest request) {
		return chatService.sendMessage(conversationId, request);
	}

	@PostMapping("/actions/{actionId}/confirm")
	public ChatActionResult confirmAction(@PathVariable UUID actionId) {
		return actionService.confirm(actionId);
	}

	@PostMapping("/actions/{actionId}/reject")
	public ChatActionResult rejectAction(@PathVariable UUID actionId) {
		return actionService.reject(actionId);
	}
}
