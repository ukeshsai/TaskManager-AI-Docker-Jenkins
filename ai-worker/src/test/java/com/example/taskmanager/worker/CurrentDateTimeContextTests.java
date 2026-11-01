package com.example.taskmanager.worker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentDateTimeContextTests {

	@Test
	void includesCurrentDateWeekdayTimeAndConfiguredTimezone() {
		ZoneId zone = ZoneId.of("Asia/Kolkata");
		Clock clock = Clock.fixed(Instant.parse("2026-11-01T07:49:43Z"), zone);
		CurrentDateTimeContext context = new CurrentDateTimeContext(zone, clock);

		String promptContext = context.systemPromptContext();

		assertTrue(promptContext.contains("Asia/Kolkata"));
		assertTrue(promptContext.contains("Sunday, 1 November 2026, 13:19:43 IST (+05:30)"));
		assertTrue(promptContext.contains("source of truth"));
	}
}
