package com.example.taskmanager.chat;

import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ChatAiResultListener {

	private static final Logger logger = LoggerFactory.getLogger(ChatAiResultListener.class);

	private final ChatService chatService;
	private final JmsTemplate jmsTemplate;
	private final ObjectMapper objectMapper;
	private final String deadLetterQueue;

	public ChatAiResultListener(
			ChatService chatService,
			JmsTemplate jmsTemplate,
			ObjectMapper objectMapper,
			@Value("${app.chat.result-dead-letter-queue}") String deadLetterQueue) {
		this.chatService = chatService;
		this.jmsTemplate = jmsTemplate;
		this.objectMapper = objectMapper;
		this.deadLetterQueue = deadLetterQueue;
	}

	@JmsListener(destination = "${app.chat.result-queue}")
	public void consume(String payload) {
		ChatJobResult result;
		try {
			result = objectMapper.readValue(payload, ChatJobResult.class);
		} catch (JacksonException exception) {
			logger.error("Moving malformed AI result to the dead-letter queue", exception);
			jmsTemplate.convertAndSend(deadLetterQueue, payload);
			return;
		}

		if (result == null || result.correlationId() == null || result.metadata() == null
				|| result.metadata().get("conversationId") == null || result.metadata().get("messageId") == null
				|| (result.successful() && (result.content() == null || result.content().isBlank()))
				|| (!result.successful() && (result.error() == null || result.error().isBlank()))) {
			logger.error("Moving an AI result without required content or correlation metadata to the dead-letter queue");
			jmsTemplate.convertAndSend(deadLetterQueue, payload);
			return;
		}

		UUID conversationId;
		Long messageId;
		try {
			conversationId = UUID.fromString(result.metadata().get("conversationId"));
			messageId = Long.valueOf(result.metadata().get("messageId"));
			if (messageId <= 0) {
				throw new IllegalArgumentException("Invalid chat message ID");
			}
		} catch (IllegalArgumentException exception) {
			logger.error("Moving an AI result with invalid chat metadata to the dead-letter queue", exception);
			jmsTemplate.convertAndSend(deadLetterQueue, payload);
			return;
		}

		if (result.successful()) {
			ChatAssistantOutput output;
			try {
				output = objectMapper.readValue(result.content(), ChatAssistantOutput.class);
			} catch (JacksonException exception) {
				logger.error("Moving an AI result with invalid assistant content to the dead-letter queue", exception);
				failInvalidResult(payload, conversationId, messageId);
				return;
			}
			if (output == null || output.reply() == null || output.reply().isBlank() || output.actions() == null) {
				logger.error("Moving an AI result with invalid assistant fields to the dead-letter queue");
				failInvalidResult(payload, conversationId, messageId);
				return;
			}
			try {
				chatService.completeQueuedMessage(conversationId, messageId, output);
			} catch (ResponseStatusException exception) {
				logger.error("Moving a task proposal that failed backend validation to the dead-letter queue", exception);
				failInvalidResult(payload, conversationId, messageId);
			}
		} else {
			chatService.failQueuedMessage(
					conversationId,
					messageId,
					result.error());
		}
	}

	private void failInvalidResult(String payload, UUID conversationId, Long messageId) {
		jmsTemplate.convertAndSend(deadLetterQueue, payload);
		chatService.failQueuedMessage(
				conversationId,
				messageId,
				"The AI returned a response that could not be validated. Please try asking again.");
	}
}
