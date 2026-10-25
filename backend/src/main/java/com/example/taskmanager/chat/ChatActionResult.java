package com.example.taskmanager.chat;

import java.util.UUID;

import com.example.taskmanager.task.TaskResponse;

public record ChatActionResult(UUID actionId, ChatAction.Status status, TaskResponse task) {
}
