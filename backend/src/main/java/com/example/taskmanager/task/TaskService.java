package com.example.taskmanager.task;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {

	private final TaskRepository taskRepository;

	public TaskService(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	public List<TaskResponse> findAll() {
		return taskRepository.findAll().stream()
				.map(TaskResponse::from)
				.toList();
	}

	public TaskResponse findById(Long id) {
		return TaskResponse.from(getTask(id));
	}

	public TaskResponse create(TaskRequest request) {
		Task task = new Task(request.title(), request.description(), request.completed());
		return TaskResponse.from(taskRepository.save(task));
	}

	public TaskResponse update(Long id, TaskRequest request) {
		Task task = getTask(id);
		task.update(request.title(), request.description(), request.completed());
		return TaskResponse.from(taskRepository.save(task));
	}

	public void delete(Long id) {
		Task task = getTask(id);
		taskRepository.delete(task);
	}

	private Task getTask(Long id) {
		return taskRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
	}
}
