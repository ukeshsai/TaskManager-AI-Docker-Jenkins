package com.example.taskmanager.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAiResultListenerTests {

	private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final String PAYLOAD = "{\"correlationId\":\"00000000-0000-0000-0000-000000000001\"}";
	private static final String ASSISTANT_CONTENT = "{\"reply\":\"Here is your answer.\",\"actions\":[]}";
	private static final Map<String, String> CHAT_METADATA = Map.of(
			"conversationId", CONVERSATION_ID.toString(),
			"messageId", "42");

	private final ChatService chatService = mock(ChatService.class);
	private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
	private final ObjectMapper objectMapper = mock(ObjectMapper.class);
	private ChatAiResultListener listener;

	@BeforeEach
	void setUp() {
		listener = new ChatAiResultListener(
				chatService,
				jmsTemplate,
				objectMapper,
				"ai.responses.task-manager.dlq");
	}

	@Test
	void completesTheCorrelatedMessageFromAWorkerResult() {
		ChatJobResult result = new ChatJobResult(
				CORRELATION_ID,
				true,
				ASSISTANT_CONTENT,
				null,
				CHAT_METADATA);
		ChatAssistantOutput output = new ChatAssistantOutput("Here is your answer.", List.of());
		when(objectMapper.readValue(PAYLOAD, ChatJobResult.class)).thenReturn(result);
		when(objectMapper.readValue(ASSISTANT_CONTENT, ChatAssistantOutput.class)).thenReturn(output);

		listener.consume(PAYLOAD);

		verify(chatService).completeQueuedMessage(CONVERSATION_ID, 42L, output);
		verify(jmsTemplate, never()).convertAndSend("ai.responses.task-manager.dlq", PAYLOAD);
	}

	@Test
	void marksAWorkerFailureOnTheCorrelatedMessage() {
		ChatJobResult result = new ChatJobResult(
				CORRELATION_ID,
				false,
				null,
				"Worker failed",
				CHAT_METADATA);
		when(objectMapper.readValue(PAYLOAD, ChatJobResult.class)).thenReturn(result);

		listener.consume(PAYLOAD);

		verify(chatService).failQueuedMessage(CONVERSATION_ID, 42L, "Worker failed");
	}
}
