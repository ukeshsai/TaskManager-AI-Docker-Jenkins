package com.example.taskmanager.chat;

public record ChatJobAction(
		ChatAction.Type type,
		Long taskId,
		String title,
		String description,
		Boolean completed) {
}
