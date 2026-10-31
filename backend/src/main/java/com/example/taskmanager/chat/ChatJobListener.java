package com.example.taskmanager.chat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatJobListener {

	private static final Logger logger = LoggerFactory.getLogger(ChatJobListener.class);
	private static final int MAX_ATTEMPTS = 3;
	private static final String FAILURE_MESSAGE =
			"The local AI could not complete this reply. Check the Ollama service and try sending your message again.";

	private final ChatJobProcessor processor;
	private final ChatService chatService;
	private final JmsTemplate jmsTemplate;
	private final ObjectMapper objectMapper;
	private final String queue;
	private final String deadLetterQueue;

	public ChatJobListener(
			ChatJobProcessor processor,
			ChatService chatService,
			JmsTemplate jmsTemplate,
			ObjectMapper objectMapper,
			@Value("${app.chat.queue}") String queue,
			@Value("${app.chat.dead-letter-queue}") String deadLetterQueue) {
		this.processor = processor;
		this.chatService = chatService;
		this.jmsTemplate = jmsTemplate;
		this.objectMapper = objectMapper;
		this.queue = queue;
		this.deadLetterQueue = deadLetterQueue;
	}

	@JmsListener(destination = "${app.chat.queue}")
	public void consume(String payload) {
		ChatJob job;
		try {
			job = objectMapper.readValue(payload, ChatJob.class);
		} catch (JacksonException exception) {
			logger.error("Moving malformed chat job to the dead-letter queue", exception);
			jmsTemplate.convertAndSend(deadLetterQueue, payload);
			return;
		}

		try {
			processor.process(job);
		} catch (RuntimeException exception) {
			if (job.attempt() + 1 >= MAX_ATTEMPTS) {
				logger.error("Chat job {} failed after {} attempts", job.messageId(), MAX_ATTEMPTS, exception);
				jmsTemplate.convertAndSend(deadLetterQueue, payload);
				chatService.failQueuedMessage(job.messageId(), FAILURE_MESSAGE);
				return;
			}

			ChatJob retry = new ChatJob(job.conversationId(), job.messageId(), job.attempt() + 1);
			try {
				jmsTemplate.convertAndSend(queue, objectMapper.writeValueAsString(retry));
			} catch (JacksonException serializationException) {
				throw new IllegalStateException("Could not encode a chat retry job", serializationException);
			}
			logger.warn("Retrying chat job {} after attempt {}", job.messageId(), job.attempt() + 1, exception);
		}
	}
}
