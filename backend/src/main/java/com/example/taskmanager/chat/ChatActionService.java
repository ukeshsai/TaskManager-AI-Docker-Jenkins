package com.example.taskmanager.chat;

import java.util.Objects;
import java.util.UUID;

import com.example.taskmanager.task.TaskRequest;
import com.example.taskmanager.task.TaskResponse;
import com.example.taskmanager.task.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatActionService {

	private final ChatActionRepository actionRepository;
	private final TaskService taskService;

	public ChatActionService(ChatActionRepository actionRepository, TaskService taskService) {
		this.actionRepository = actionRepository;
		this.taskService = taskService;
	}

	@Transactional
	public ChatActionResult confirm(UUID actionId) {
		ChatAction action = getPendingAction(actionId);
		if (action.getType() != ChatAction.Type.CREATE) {
			TaskResponse currentTask = taskService.findById(action.getTaskId());
			if (!matchesProposalSnapshot(action, currentTask)) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"The task changed after this proposal. Ask the assistant to refresh it before applying.");
			}
		}
		TaskResponse task = switch (action.getType()) {
			case CREATE -> taskService.create(new TaskRequest(
					action.getTitle(),
					action.getDescription(),
					Boolean.TRUE.equals(action.getCompleted())));
			case UPDATE -> taskService.update(action.getTaskId(), new TaskRequest(
					action.getTitle(),
					action.getDescription(),
					Boolean.TRUE.equals(action.getCompleted())));
			case DELETE -> {
				taskService.delete(action.getTaskId());
				yield null;
			}
		};
		action.markApplied();
		actionRepository.save(action);
		return new ChatActionResult(action.getId(), action.getStatus(), task);
	}

	@Transactional
	public ChatActionResult reject(UUID actionId) {
		ChatAction action = getPendingAction(actionId);
		action.markRejected();
		actionRepository.save(action);
		return new ChatActionResult(action.getId(), action.getStatus(), null);
	}

	private boolean matchesProposalSnapshot(ChatAction action, TaskResponse task) {
		return Objects.equals(action.getOriginalTitle(), task.title())
				&& Objects.equals(action.getOriginalDescription(), task.description())
				&& Objects.equals(action.getOriginalCompleted(), task.completed());
	}

	private ChatAction getPendingAction(UUID actionId) {
		ChatAction action = actionRepository.findById(actionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task change not found"));
		if (action.getStatus() != ChatAction.Status.PENDING) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This task change has already been resolved");
		}
		return action;
	}
}
