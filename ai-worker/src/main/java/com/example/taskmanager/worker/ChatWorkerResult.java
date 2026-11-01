package com.example.taskmanager.worker;

import java.util.Map;
import java.util.UUID;

public record ChatWorkerResult(
		UUID correlationId,
		boolean successful,
		String content,
		String error,
		Map<String, String> metadata) {

	public static ChatWorkerResult failed(ChatWorkerJob job, String error) {
		return new ChatWorkerResult(
				job.correlationId(),
				false,
				null,
				error,
				job.metadata());
	}
}
