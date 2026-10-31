package com.example.taskmanager.chat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatQueuePublisher {

	private final JmsTemplate jmsTemplate;
	private final ObjectMapper objectMapper;
	private final String queue;

	public ChatQueuePublisher(
			JmsTemplate jmsTemplate,
			ObjectMapper objectMapper,
			@Value("${app.chat.queue}") String queue) {
		this.jmsTemplate = jmsTemplate;
		this.objectMapper = objectMapper;
		this.queue = queue;
	}

	public void enqueue(ChatJob job) {
		try {
			jmsTemplate.convertAndSend(queue, objectMapper.writeValueAsString(job));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not encode the chat job", exception);
		}
	}
}
