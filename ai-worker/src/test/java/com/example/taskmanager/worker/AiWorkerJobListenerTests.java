package com.example.taskmanager.worker;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiWorkerJobListenerTests {

	private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final Map<String, String> METADATA = Map.of("requestor", "test");
	private static final Map<String, Object> RESPONSE_SCHEMA = Map.of("type", "object");
	private static final ChatWorkerJob FIRST_ATTEMPT = new ChatWorkerJob(
			CORRELATION_ID,
			0,
			"ai.responses.task-manager",
			"Be concise.",
			List.of(new ChatPromptMessage("user", "Hello")),
			RESPONSE_SCHEMA,
			METADATA);
	private static final ChatWorkerJob RETRY = new ChatWorkerJob(
			CORRELATION_ID,
			1,
			FIRST_ATTEMPT.replyTo(),
			FIRST_ATTEMPT.systemPrompt(),
			FIRST_ATTEMPT.messages(),
			FIRST_ATTEMPT.responseSchema(),
			FIRST_ATTEMPT.metadata());
	private static final String PAYLOAD = "{\"correlationId\":\"00000000-0000-0000-0000-000000000001\"}";
	private static final String RETRY_PAYLOAD = "{\"attempt\":1}";

	private final AiWorkerProcessor processor = mock(AiWorkerProcessor.class);
	private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
	private final ObjectMapper objectMapper = mock(ObjectMapper.class);
	private AiWorkerJobListener listener;

	@BeforeEach
	void setUp() {
		listener = new AiWorkerJobListener(
				processor,
				jmsTemplate,
				objectMapper,
				"chat.requests",
				"chat.requests.dlq");
	}

	@Test
	void sendsSuccessfulWorkToTheResultQueue() throws Exception {
		ChatWorkerResult result = new ChatWorkerResult(
				CORRELATION_ID,
				true,
				"Hello",
				null,
				METADATA);
		when(objectMapper.readValue(PAYLOAD, ChatWorkerJob.class)).thenReturn(FIRST_ATTEMPT);
		when(processor.process(FIRST_ATTEMPT)).thenReturn(result);
		when(objectMapper.writeValueAsString(result)).thenReturn("{\"successful\":true}");

		listener.consume(PAYLOAD);

		verify(jmsTemplate).convertAndSend("ai.responses.task-manager", "{\"successful\":true}");
		verify(jmsTemplate, never()).convertAndSend("chat.requests", RETRY_PAYLOAD);
	}

	@Test
	void republishesAJobAfterOneFailedAttempt() throws Exception {
		when(objectMapper.readValue(PAYLOAD, ChatWorkerJob.class)).thenReturn(FIRST_ATTEMPT);
		doThrow(new IllegalStateException("Ollama is unavailable")).when(processor).process(FIRST_ATTEMPT);
		when(objectMapper.writeValueAsString(RETRY)).thenReturn(RETRY_PAYLOAD);

		listener.consume(PAYLOAD);

		verify(jmsTemplate).convertAndSend("chat.requests", RETRY_PAYLOAD);
		verify(jmsTemplate, never()).convertAndSend("ai.responses.task-manager", PAYLOAD);
	}

	@Test
	void sendsFailureResultAfterTheLastAttempt() throws Exception {
		ChatWorkerJob lastAttempt = new ChatWorkerJob(
				CORRELATION_ID,
				2,
				FIRST_ATTEMPT.replyTo(),
				FIRST_ATTEMPT.systemPrompt(),
				FIRST_ATTEMPT.messages(),
				FIRST_ATTEMPT.responseSchema(),
				FIRST_ATTEMPT.metadata());
		ChatWorkerResult failure = ChatWorkerResult.failed(
				lastAttempt,
				"The local AI could not complete this reply. Check the Ollama service and try sending your message again.");
		when(objectMapper.readValue(PAYLOAD, ChatWorkerJob.class)).thenReturn(lastAttempt);
		doThrow(new IllegalStateException("Ollama is unavailable")).when(processor).process(lastAttempt);
		when(objectMapper.writeValueAsString(failure)).thenReturn("{\"successful\":false}");

		listener.consume(PAYLOAD);

		verify(jmsTemplate).convertAndSend("chat.requests.dlq", PAYLOAD);
		verify(jmsTemplate).convertAndSend("ai.responses.task-manager", "{\"successful\":false}");
	}
}
