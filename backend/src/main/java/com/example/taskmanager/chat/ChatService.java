package com.example.taskmanager.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Validator;

import com.example.taskmanager.chat.OllamaChatClient.ActionProposal;
import com.example.taskmanager.chat.OllamaChatClient.AssistantOutput;
import com.example.taskmanager.task.TaskRequest;
import com.example.taskmanager.task.TaskResponse;
import com.example.taskmanager.task.TaskService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jms.JmsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

	private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
	private static final int MAX_ACTIONS_PER_REPLY = 5;

	private final ChatConversationRepository conversationRepository;
	private final ChatMessageRepository messageRepository;
	private final ChatActionRepository actionRepository;
	private final TaskService taskService;
	private final TaskQueryResponder taskQueryResponder;
	private final OllamaChatClient ollamaChatClient;
	private final ChatQueuePublisher queuePublisher;
	private final Validator validator;

	public ChatService(
			ChatConversationRepository conversationRepository,
			ChatMessageRepository messageRepository,
			ChatActionRepository actionRepository,
			TaskService taskService,
			TaskQueryResponder taskQueryResponder,
			OllamaChatClient ollamaChatClient,
			ChatQueuePublisher queuePublisher,
			Validator validator) {
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.actionRepository = actionRepository;
		this.taskService = taskService;
		this.taskQueryResponder = taskQueryResponder;
		this.ollamaChatClient = ollamaChatClient;
		this.queuePublisher = queuePublisher;
		this.validator = validator;
	}

	public ChatConversationResponse createConversation() {
		return ChatConversationResponse.from(conversationRepository.save(new ChatConversation()));
	}

	public List<ChatConversationResponse> listConversations() {
		return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
				.map(ChatConversationResponse::from)
				.toList();
	}

	@Transactional
	public void deleteConversation(UUID conversationId) {
		requireConversation(conversationId);
		actionRepository.deleteAllByConversationId(conversationId);
		messageRepository.deleteAllByConversationId(conversationId);
		conversationRepository.deleteById(conversationId);
	}

	public List<ChatMessageResponse> listMessages(UUID conversationId) {
		requireConversation(conversationId);
		return messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId).stream()
				.map(message -> ChatMessageResponse.from(
						message,
						actionRepository.findAllByMessageIdOrderByCreatedAtAsc(message.getId())))
				.toList();
	}

	public ChatMessageResponse sendMessage(UUID conversationId, ChatRequest request) {
		ChatConversation conversation = requireConversation(conversationId);
		if (messageRepository.existsByConversationIdAndProcessingStatus(
				conversationId,
				ChatMessage.ProcessingStatus.PROCESSING)) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Wait for the current assistant reply before sending another message.");
		}

		conversation.titleFromFirstMessage(request.content().trim());
		conversationRepository.save(conversation);

		ChatMessage userMessage = new ChatMessage(conversation, "user", request.content().trim());
		userMessage.markProcessing();
		userMessage = messageRepository.save(userMessage);

		List<ChatMessage> history = new ArrayList<>(
				messageRepository.findTop12ByConversationIdOrderByCreatedAtDesc(conversationId));
		Collections.reverse(history);

		List<TaskResponse> tasks = taskService.findAll();
		var queryReply = taskQueryResponder.reply(request.content(), tasks);
		if (queryReply.isPresent()) {
			userMessage.markCompleted();
			messageRepository.save(userMessage);
			ChatMessage assistantMessage = messageRepository.save(
					new ChatMessage(conversation, "assistant", queryReply.get()));
			return ChatMessageResponse.from(assistantMessage, List.of());
		}

		try {
			queuePublisher.enqueue(new ChatJob(conversationId, userMessage.getId(), 0));
		} catch (JmsException | IllegalStateException exception) {
			logger.error("Could not enqueue chat job {}", userMessage.getId(), exception);
			userMessage.markFailed("The chat queue is unavailable. Please try sending your message again.");
			messageRepository.save(userMessage);
		}
		return ChatMessageResponse.from(userMessage, List.of());
	}

	@Transactional
	public void completeQueuedMessage(Long messageId, AssistantOutput output) {
		ChatMessage userMessage = messageRepository.findById(messageId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat message not found"));
		if (userMessage.getProcessingStatus() != ChatMessage.ProcessingStatus.PROCESSING) {
			return;
		}
		if (output.actions().size() > MAX_ACTIONS_PER_REPLY) {
			logger.warn("The AI proposed more than {} task changes", MAX_ACTIONS_PER_REPLY);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI proposed too many task changes");
		}

		ChatConversation conversation = userMessage.getConversation();
		ChatMessage assistantMessage = messageRepository.save(
				new ChatMessage(conversation, "assistant", replyFor(output)));
		List<ChatAction> actions = output.actions().stream()
				.map(proposal -> createPendingAction(conversation, assistantMessage, proposal))
				.toList();
		actionRepository.saveAll(actions);
		userMessage.markCompleted();
		messageRepository.save(userMessage);
	}

	@Transactional
	public void failQueuedMessage(Long messageId, String error) {
		messageRepository.findById(messageId).ifPresent(message -> {
			if (message.getProcessingStatus() == ChatMessage.ProcessingStatus.PROCESSING) {
				message.markFailed(error);
				messageRepository.save(message);
			}
		});
	}

	private String replyFor(AssistantOutput output) {
		if (output.actions().isEmpty()) {
			return output.reply().trim();
		}
		if (output.actions().size() == 1) {
			return "I prepared the task change below. It has not been applied yet; confirm or decline it.";
		}
		return "I prepared the task changes below. None have been applied yet; confirm or decline each one.";
	}

	private ChatAction createPendingAction(
			ChatConversation conversation,
			ChatMessage message,
			ActionProposal proposal) {
		if (proposal.type() == null) {
			throw invalidModelAction("missing action type");
		}

		return switch (proposal.type()) {
			case CREATE -> createTaskProposal(conversation, message, proposal);
			case UPDATE -> updateTaskProposal(conversation, message, proposal);
			case DELETE -> deleteTaskProposal(conversation, message, proposal);
		};
	}

	private ChatAction createTaskProposal(
			ChatConversation conversation,
			ChatMessage message,
			ActionProposal proposal) {
		TaskRequest request = new TaskRequest(
				proposal.title(),
				proposal.description(),
				Boolean.TRUE.equals(proposal.completed()));
		validateProposedTask(request);
		return new ChatAction(
				conversation,
				message,
				ChatAction.Type.CREATE,
				null,
				request.title().trim(),
				request.description(),
				request.completed(),
				null,
				null,
				null);
	}

	private ChatAction updateTaskProposal(
			ChatConversation conversation,
			ChatMessage message,
			ActionProposal proposal) {
		if (proposal.taskId() == null) {
			throw invalidModelAction("update action did not include a task ID");
		}
		if (proposal.title() == null || proposal.completed() == null) {
			throw invalidModelAction("update action omitted required task fields");
		}
		TaskResponse existing = requireExistingTask(proposal.taskId());
		TaskRequest request = new TaskRequest(
				proposal.title(),
				proposal.description(),
				Boolean.TRUE.equals(proposal.completed()));
		validateProposedTask(request);
		return new ChatAction(
				conversation,
				message,
				ChatAction.Type.UPDATE,
				existing.id(),
				request.title().trim(),
				request.description(),
				request.completed(),
				existing.title(),
				existing.description(),
				existing.completed());
	}

	private ChatAction deleteTaskProposal(
			ChatConversation conversation,
			ChatMessage message,
			ActionProposal proposal) {
		if (proposal.taskId() == null) {
			throw invalidModelAction("delete action did not include a task ID");
		}
		TaskResponse existing = requireExistingTask(proposal.taskId());
		return new ChatAction(
				conversation,
				message,
				ChatAction.Type.DELETE,
				existing.id(),
				existing.title(),
				existing.description(),
				existing.completed(),
				existing.title(),
				existing.description(),
				existing.completed());
	}

	private void validateProposedTask(TaskRequest request) {
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			logger.warn("The AI proposed task fields that failed validation: {}",
					violations.stream().map(violation -> violation.getPropertyPath().toString()).toList());
			throw invalidModelAction("task fields failed validation");
		}
	}

	private TaskResponse requireExistingTask(Long taskId) {
		try {
			return taskService.findById(taskId);
		} catch (ResponseStatusException exception) {
			if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw invalidModelAction("the proposal referenced a task that does not exist");
			}
			throw exception;
		}
	}

	private ChatConversation requireConversation(UUID conversationId) {
		return conversationRepository.findById(conversationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
	}

	private ResponseStatusException invalidModelAction(String reason) {
		logger.warn("Rejected invalid task proposal from the local AI: {}", reason);
		return new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"The AI returned a task change that could not be validated");
	}
}
