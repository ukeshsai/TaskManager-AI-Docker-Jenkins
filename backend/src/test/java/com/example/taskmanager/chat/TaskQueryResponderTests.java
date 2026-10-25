package com.example.taskmanager.chat;

import java.time.Instant;
import java.util.List;

import com.example.taskmanager.task.TaskResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskQueryResponderTests {

	private final TaskQueryResponder responder = new TaskQueryResponder();

	private final List<TaskResponse> tasks = List.of(
			new TaskResponse(
					3L,
					"Code Analysis tomorrow for CIF",
					"Code analysis for CIF tomorrow",
					false,
					Instant.parse("2026-10-24T13:07:09Z")),
			new TaskResponse(
					4L,
					"Review design",
					"Check the new layout",
					true,
					Instant.parse("2026-10-24T13:08:09Z")));

	@Test
	void answersPendingTaskLookupFromStoredTasks() {
		String reply = responder.reply(
				"Check my task list and let me know is there any task pending which is related to CIF?",
				tasks).orElseThrow();

		assertTrue(reply.contains("1 pending task related to \"CIF\""));
		assertTrue(reply.contains("Code Analysis tomorrow for CIF"));
		assertTrue(reply.contains("Code analysis for CIF tomorrow"));
	}

	@Test
	void reportsWhenNoTaskMatchesTheLookup() {
		String reply = responder.reply(
				"Is there any task pending related to payroll?",
				tasks).orElseThrow();

		assertEquals("No pending tasks related to \"payroll\" were found.", reply);
	}

	@Test
	void doesNotTreatExplicitCreateRequestAsAReadOnlyQuery() {
		assertTrue(responder.reply("Please create a task for CIF", tasks).isEmpty());
	}
}
