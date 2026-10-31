package com.example.taskmanager.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.example.taskmanager.chat.OllamaChatClient.AssistantOutput;
import com.example.taskmanager.task.TaskResponse;
import com.example.taskmanager.task.TaskService;

import org.springframework.stereotype.Component;

@Component
public class ChatJobProcessor {

	private final ChatMessageRepository messageRepository;
	private final TaskService taskService;
	private final OllamaChatClient ollamaChatClient;
	private final ChatService chatService;

	public ChatJobProcessor(
			ChatMessageRepository messageRepository,
			TaskService taskService,
			OllamaChatClient ollamaChatClient,
			ChatService chatService) {
		this.messageRepository = messageRepository;
		this.taskService = taskService;
		this.ollamaChatClient = ollamaChatClient;
		this.chatService = chatService;
	}

	public void process(ChatJob job) {
		ChatMessage userMessage = messageRepository.findByIdAndConversationId(
				job.messageId(),
				job.conversationId()).orElse(null);
		if (userMessage == null || userMessage.getProcessingStatus() != ChatMessage.ProcessingStatus.PROCESSING) {
			return;
		}

		UUID conversationId = job.conversationId();
		List<ChatMessage> history = new ArrayList<>(
				messageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(conversationId));
		Collections.reverse(history);
		List<TaskResponse> tasks = taskService.findAll();
		AssistantOutput output = ollamaChatClient.complete(history, tasks);
		chatService.completeQueuedMessage(job.messageId(), output);
	}
}
