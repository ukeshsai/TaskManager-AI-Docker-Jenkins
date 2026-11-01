package com.example.taskmanager.worker;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiWorkerJobListener {

	private static final Logger logger = LoggerFactory.getLogger(AiWorkerJobListener.class);
	private static final int MAX_ATTEMPTS = 3;
	private static final String ALLOWED_REPLY_TO_PATTERN = "^ai\\.responses\\.[A-Za-z0-9][A-Za-z0-9._-]{0,62}$";
	private static final String FAILURE_MESSAGE =
			"The local AI could not complete this reply. Check the Ollama service and try sending your message again.";

	private final AiWorkerProcessor processor;
	private final JmsTemplate jmsTemplate;
	private final ObjectMapper objectMapper;
	private final String requestQueue;
	private final String requestDeadLetterQueue;

	public AiWorkerJobListener(
			AiWorkerProcessor processor,
			JmsTemplate jmsTemplate,
			ObjectMapper objectMapper,
			@Value("${app.ai.request-queue}") String requestQueue,
			@Value("${app.ai.request-dead-letter-queue}") String requestDeadLetterQueue) {
		this.processor = processor;
		this.jmsTemplate = jmsTemplate;
		this.objectMapper = objectMapper;
		this.requestQueue = requestQueue;
		this.requestDeadLetterQueue = requestDeadLetterQueue;
	}

	@JmsListener(destination = "${app.ai.request-queue}")
	public void consume(String payload) {
		ChatWorkerJob job;
		try {
			job = objectMapper.readValue(payload, ChatWorkerJob.class);
		} catch (JacksonException exception) {
			logger.error("Moving malformed AI job to the dead-letter queue", exception);
			jmsTemplate.convertAndSend(requestDeadLetterQueue, payload);
			return;
		}

		if (job == null || job.correlationId() == null || job.attempt() < 0 || job.replyTo() == null
				|| !job.replyTo().matches(ALLOWED_REPLY_TO_PATTERN) || job.systemPrompt() == null
				|| job.systemPrompt().isBlank() || job.messages() == null || job.messages().isEmpty()
				|| job.responseSchema() == null || job.metadata() == null) {
			logger.error("Moving an AI job without required context to the dead-letter queue");
			jmsTemplate.convertAndSend(requestDeadLetterQueue, payload);
			return;
		}

		ChatWorkerResult result;
		try {
			result = processor.process(job);
		} catch (RuntimeException exception) {
			handleProcessingFailure(job, payload, exception);
			return;
		}

		publishResult(job.replyTo(), result);
	}

	private void handleProcessingFailure(ChatWorkerJob job, String payload, RuntimeException exception) {
		if (job.attempt() + 1 >= MAX_ATTEMPTS) {
			logger.error("AI job {} failed after {} attempts", job.correlationId(), MAX_ATTEMPTS, exception);
			jmsTemplate.convertAndSend(requestDeadLetterQueue, payload);
			publishResult(job.replyTo(), ChatWorkerResult.failed(job, FAILURE_MESSAGE));
			return;
		}

		ChatWorkerJob retry = new ChatWorkerJob(
				job.correlationId(),
				job.attempt() + 1,
				job.replyTo(),
				job.systemPrompt(),
				job.messages(),
				job.responseSchema(),
				job.metadata());
		try {
			jmsTemplate.convertAndSend(requestQueue, objectMapper.writeValueAsString(retry));
		} catch (JacksonException serializationException) {
			throw new IllegalStateException("Could not encode an AI job retry", serializationException);
		}
		logger.warn("Retrying AI job {} after attempt {}", job.correlationId(), job.attempt() + 1, exception);
	}

	private void publishResult(String replyTo, ChatWorkerResult result) {
		try {
			jmsTemplate.convertAndSend(replyTo, objectMapper.writeValueAsString(result));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not encode the AI job result", exception);
		}
	}
}
