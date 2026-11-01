package com.example.taskmanager.worker;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentDateTimeContext {

	private static final DateTimeFormatter FORMATTER =
			DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu, HH:mm:ss z (XXX)", Locale.ENGLISH);
	private static final String INSTRUCTIONS = """
			The current date and time is supplied below. Use it as the source of truth when answering questions about
			today, the current time, weekdays, or relative dates. Respect the displayed timezone and offset. Do not guess
			the current date/time or claim to know another location's local time unless its timezone is provided.
			For date calculations, calculate from this supplied date rather than relying on memorized real-world dates.
			""";

	private final ZoneId zone;
	private final Clock clock;

	@Autowired
	public CurrentDateTimeContext(@Value("${app.ai.time-zone:Asia/Kolkata}") String timeZone) {
		this(ZoneId.of(timeZone), Clock.system(ZoneId.of(timeZone)));
	}

	CurrentDateTimeContext(ZoneId zone, Clock clock) {
		this.zone = zone;
		this.clock = clock.withZone(zone);
	}

	public String systemPromptContext() {
		ZonedDateTime now = ZonedDateTime.now(clock);
		return INSTRUCTIONS + "\nCurrent date and time (" + zone.getId() + "): " + FORMATTER.format(now) + ".";
	}
}
