package com.example.taskmanager.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.taskmanager.task.TaskResponse;

import org.springframework.stereotype.Component;

@Component
public class TaskQueryResponder {

	private static final Pattern TASK_MENTION = Pattern.compile("\\b(tasks?|to-?dos?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern INFORMATION_REQUEST = Pattern.compile(
			"\\b(check|show|list|find|search|tell me|let me know|is there|are there|any|what|which|how many|do i have)\\b",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern MUTATION_REQUEST = Pattern.compile(
			"^\\s*(please\\s+)?(create|add|make|schedule|edit|update|change|rename|delete|remove|mark|complete|finish)\\b"
					+ "|\\b(i want you to|i need you to|please|can you|could you|would you)\\s+"
					+ "(create|add|make|schedule|edit|update|change|rename|delete|remove|mark|complete|finish)\\b",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern RELATED_TERM = Pattern.compile(
			"\\b(?:related to|about|mentioning|containing)\\s+(.+?)[?.!]*\\s*$",
			Pattern.CASE_INSENSITIVE);
	public Optional<String> reply(String message, List<TaskResponse> tasks) {
		if (!isReadOnlyTaskQuery(message)) {
			return Optional.empty();
		}

		String term = relatedTerm(message);
		boolean pendingOnly = hasAnyWord(message, "pending", "incomplete", "unfinished", "to do", "not done");
		boolean completedOnly = !pendingOnly && hasAnyWord(message, "completed", "complete", "finished", "done");

		List<TaskResponse> matches = tasks.stream()
				.filter(task -> pendingOnly ? !task.completed() : !completedOnly || task.completed())
				.filter(task -> term.isEmpty() || taskMatches(task, term))
				.toList();

		if (matches.isEmpty()) {
			String qualifier = pendingOnly ? "pending " : completedOnly ? "completed " : "";
			String subject = term.isEmpty() ? "tasks" : "tasks related to \"" + term + "\"";
			return Optional.of("No " + qualifier + subject + " were found.");
		}

		String qualifier = pendingOnly ? " pending" : completedOnly ? " completed" : "";
		String subject = term.isEmpty() ? "task" : "task related to \"" + term + "\"";
		String pluralSubject = term.isEmpty() ? "tasks" : "tasks related to \"" + term + "\"";
		String heading = matches.size() == 1
				? "I found 1" + qualifier + " " + subject + ":"
				: "I found " + matches.size() + qualifier + " " + pluralSubject + ":";
		String details = matches.stream()
				.map(task -> "- " + task.title()
						+ (task.description() == null || task.description().isBlank()
								? ""
								: " — " + task.description()))
				.collect(java.util.stream.Collectors.joining("\n"));
		return Optional.of(heading + "\n" + details);
	}

	private boolean isReadOnlyTaskQuery(String message) {
		return TASK_MENTION.matcher(message).find()
				&& INFORMATION_REQUEST.matcher(message).find()
				&& !MUTATION_REQUEST.matcher(message).find();
	}

	private String relatedTerm(String message) {
		Matcher matcher = RELATED_TERM.matcher(message);
		if (!matcher.find()) {
			return "";
		}
		return matcher.group(1).trim().replaceAll("[?.!]+$", "").trim();
	}

	private boolean taskMatches(TaskResponse task, String term) {
		String searchableText = (task.title() + " " + (task.description() == null ? "" : task.description()))
				.toLowerCase(Locale.ROOT);
		return searchableText.contains(term.toLowerCase(Locale.ROOT));
	}

	private boolean hasAnyWord(String message, String... words) {
		String normalized = message.toLowerCase(Locale.ROOT);
		for (String word : words) {
			if (normalized.contains(word)) {
				return true;
			}
		}
		return false;
	}
}
