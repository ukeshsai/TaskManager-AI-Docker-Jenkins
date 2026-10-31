package com.example.taskmanager.chat;

import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatJobListenerTests {

	private static final String PAYLOAD = "{\"conversationId\":\"00000000-0000-0000-0000-000000000001\","
			+ "\"messageId\":42,\"attempt\":0}";
	private static final String RETRY_PAYLOAD = "{\"conversationId\":\"00000000-0000-0000-0000-000000000001\","
			+ "\"messageId\":42,\"attempt\":1}";

	private final ChatJobProcessor processor = mock(ChatJobProcessor.class);
	private final ChatService chatService = mock(ChatService.class);
	private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
	private final ObjectMapper objectMapper = mock(ObjectMapper.class);
	private ChatJobListener listener;

	@BeforeEach
	void setUp() {
		listener = new ChatJobListener(
				processor,
				chatService,
				jmsTemplate,
				objectMapper,
				"chat.requests",
				"chat.requests.dlq");
	}

	@Test
	void processesAQueuedChatJob() {
		ChatJob job = new ChatJob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 42L, 0);
		when(objectMapper.readValue(PAYLOAD, ChatJob.class)).thenReturn(job);

		listener.consume(PAYLOAD);

		verify(processor).process(job);
		verify(jmsTemplate, never()).convertAndSend("chat.requests", RETRY_PAYLOAD);
	}

	@Test
	void republishesAJobAfterAProcessingFailure() {
		ChatJob job = new ChatJob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 42L, 0);
		when(objectMapper.readValue(PAYLOAD, ChatJob.class)).thenReturn(job);
		when(objectMapper.writeValueAsString(new ChatJob(job.conversationId(), job.messageId(), 1)))
				.thenReturn(RETRY_PAYLOAD);
		doThrow(new IllegalStateException("Ollama is unavailable")).when(processor).process(job);

		listener.consume(PAYLOAD);

		verify(jmsTemplate).convertAndSend("chat.requests", RETRY_PAYLOAD);
		verify(chatService, never()).failQueuedMessage(job.messageId(), "The local AI could not complete this reply. "
				+ "Check the Ollama service and try sending your message again.");
	}

	@Test
	void movesAnExhaustedJobToDeadLetterAndMarksItFailed() {
		ChatJob job = new ChatJob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 42L, 2);
		when(objectMapper.readValue(PAYLOAD, ChatJob.class)).thenReturn(job);
		doThrow(new IllegalStateException("Ollama is unavailable")).when(processor).process(job);

		listener.consume(PAYLOAD);

		verify(jmsTemplate).convertAndSend("chat.requests.dlq", PAYLOAD);
		verify(chatService).failQueuedMessage(
				job.messageId(),
				"The local AI could not complete this reply. Check the Ollama service and try sending your message again.");
	}

	@Test
	void propagatesFailureWhenRetryCannotBeSerialized() {
		ChatJob job = new ChatJob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 42L, 0);
		when(objectMapper.readValue(PAYLOAD, ChatJob.class)).thenReturn(job);
		doThrow(new IllegalStateException("Ollama is unavailable")).when(processor).process(job);
		when(objectMapper.writeValueAsString(new ChatJob(job.conversationId(), job.messageId(), 1)))
				.thenThrow(new IllegalStateException("Could not encode the retry"));

		assertThrows(IllegalStateException.class, () -> listener.consume(PAYLOAD));
	}
}
