package com.example.taskmanager.chat;

import java.util.UUID;

public record ChatJob(UUID conversationId, Long messageId, int attempt) {
}
