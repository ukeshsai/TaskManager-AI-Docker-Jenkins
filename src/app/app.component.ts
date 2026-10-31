import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, Subscription, catchError, finalize, switchMap, takeWhile, tap, timer } from 'rxjs';
import { ChatAction, ChatConversation, ChatMessage } from './chat.model';
import { ChatService } from './chat.service';
import { Task, TaskRequest } from './task.model';
import { TaskService } from './task.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly taskService = inject(TaskService);
  private readonly chatService = inject(ChatService);

  readonly taskForm = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    completed: [false]
  });
  readonly chatForm = this.formBuilder.nonNullable.group({
    content: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  tasks: Task[] = [];
  conversations: ChatConversation[] = [];
  chatMessages: ChatMessage[] = [];
  selectedConversationId: string | null = null;
  editingTask: Task | null = null;
  loading = false;
  saving = false;
  chatLoading = false;
  creatingConversation = false;
  deletingConversationId: string | null = null;
  sendingMessage = false;
  deletingTaskId: number | null = null;
  resolvingActionIds = new Set<string>();
  errorMessage = '';
  chatErrorMessage = '';
  readonly today = new Date();
  private chatPollingSubscription: Subscription | null = null;

  get hasProcessingChatMessage(): boolean {
    return this.chatMessages.some(message => message.processingStatus === 'PROCESSING');
  }

  ngOnInit(): void {
    this.loadTasks();
    this.loadConversations();
  }

  ngOnDestroy(): void {
    this.stopPollingMessages();
  }

  loadTasks(): void {
    this.loading = true;
    this.errorMessage = '';
    this.taskService.getTasks()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: tasks => this.tasks = tasks,
        error: () => this.errorMessage = 'Could not load tasks. Check the API connection and try again.'
      });
  }

  startEditing(task: Task): void {
    this.editingTask = task;
    this.taskForm.setValue({
      title: task.title,
      description: task.description ?? '',
      completed: task.completed
    });
    this.errorMessage = '';
  }

  cancelEditing(): void {
    this.editingTask = null;
    this.taskForm.reset({ title: '', description: '', completed: false });
    this.errorMessage = '';
  }

  saveTask(): void {
    if (this.taskForm.invalid || this.saving) {
      this.taskForm.markAllAsTouched();
      return;
    }

    const editingTask = this.editingTask;
    const request: TaskRequest = {
      ...this.taskForm.getRawValue(),
      description: this.taskForm.controls.description.value.trim() || null
    };
    const save = editingTask
      ? this.taskService.updateTask(editingTask.id, request)
      : this.taskService.createTask(request);

    this.saving = true;
    this.errorMessage = '';
    save.pipe(finalize(() => this.saving = false)).subscribe({
      next: savedTask => {
        if (editingTask) {
          this.tasks = this.tasks.map(task => task.id === savedTask.id ? savedTask : task);
        } else {
          this.tasks = [savedTask, ...this.tasks];
        }
        if (this.editingTask === editingTask) {
          this.cancelEditing();
        }
      },
      error: () => this.errorMessage = 'Could not save the task. Please try again.'
    });
  }

  toggleCompleted(task: Task): void {
    const request: TaskRequest = {
      title: task.title,
      description: task.description,
      completed: !task.completed
    };
    this.errorMessage = '';
    this.taskService.updateTask(task.id, request).subscribe({
      next: updatedTask => {
        this.tasks = this.tasks.map(item => item.id === updatedTask.id ? updatedTask : item);
      },
      error: () => this.errorMessage = 'Could not update the task. Please try again.'
    });
  }

  deleteTask(task: Task): void {
    if (this.deletingTaskId !== null) {
      return;
    }

    this.deletingTaskId = task.id;
    this.errorMessage = '';
    this.taskService.deleteTask(task.id)
      .pipe(finalize(() => this.deletingTaskId = null))
      .subscribe({
        next: () => {
          this.tasks = this.tasks.filter(item => item.id !== task.id);
          if (this.editingTask?.id === task.id) {
            this.cancelEditing();
          }
        },
        error: () => this.errorMessage = 'Could not delete the task. Please try again.'
      });
  }

  loadConversations(): void {
    this.chatLoading = true;
    this.chatErrorMessage = '';
    this.chatService.getConversations()
      .pipe(finalize(() => this.chatLoading = false))
      .subscribe({
        next: conversations => {
          this.conversations = conversations;
          if (conversations.length > 0) {
            this.selectConversation(conversations[0].id);
          } else {
            this.createConversation();
          }
        },
        error: () => this.chatErrorMessage = 'Could not load chat history. Please try again.'
      });
  }

  createConversation(): void {
    if (this.creatingConversation) {
      return;
    }

    this.stopPollingMessages();
    this.creatingConversation = true;
    this.chatErrorMessage = '';
    this.chatService.createConversation()
      .pipe(finalize(() => this.creatingConversation = false))
      .subscribe({
        next: conversation => {
          this.conversations = [conversation, ...this.conversations];
          this.selectedConversationId = conversation.id;
          this.chatMessages = [];
        },
        error: () => this.chatErrorMessage = 'Could not start a new conversation. Please try again.'
      });
  }

  deleteSelectedConversation(): void {
    const conversationId = this.selectedConversationId;
    if (!conversationId || this.deletingConversationId
      || !globalThis.confirm('Delete this conversation and its messages? This cannot be undone.')) {
      return;
    }

    this.deletingConversationId = conversationId;
    this.chatErrorMessage = '';
    this.chatService.deleteConversation(conversationId)
      .pipe(finalize(() => this.deletingConversationId = null))
      .subscribe({
        next: () => {
          this.conversations = this.conversations.filter(conversation => conversation.id !== conversationId);
          if (this.selectedConversationId === conversationId) {
            this.chatMessages = [];
            this.selectedConversationId = null;
            if (this.conversations.length > 0) {
              this.selectConversation(this.conversations[0].id);
            } else {
              this.createConversation();
            }
          }
        },
        error: () => this.chatErrorMessage = 'Could not delete this conversation. Please try again.'
      });
  }

  selectConversation(conversationId: string): void {
    this.stopPollingMessages();
    this.selectedConversationId = conversationId;
    this.chatLoading = true;
    this.chatErrorMessage = '';
    this.chatService.getMessages(conversationId)
      .pipe(finalize(() => this.chatLoading = false))
      .subscribe({
        next: messages => {
          if (this.selectedConversationId === conversationId) {
            this.chatMessages = messages;
            if (messages.some(message => message.processingStatus === 'PROCESSING')) {
              this.pollMessages(conversationId);
            }
          }
        },
        error: () => {
          if (this.selectedConversationId === conversationId) {
            this.chatErrorMessage = 'Could not load this conversation. Please try again.';
          }
        }
      });
  }

  sendChatMessage(): void {
    const content = this.chatForm.controls.content.value.trim();
    const conversationId = this.selectedConversationId;
    if (!content || !conversationId || this.sendingMessage || this.hasProcessingChatMessage) {
      this.chatForm.controls.content.markAsTouched();
      return;
    }

    const optimisticMessageId = -Date.now();
    const userMessage: ChatMessage = {
      id: optimisticMessageId,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
      actions: []
    };
    this.chatMessages = [...this.chatMessages, userMessage];
    this.chatForm.reset({ content: '' });
    this.sendingMessage = true;
    this.chatErrorMessage = '';
    this.chatService.sendMessage(conversationId, content)
      .pipe(finalize(() => this.sendingMessage = false))
      .subscribe({
        next: message => {
          if (this.selectedConversationId === conversationId) {
            if (message.role === 'user') {
              this.chatMessages = this.chatMessages.map(currentMessage =>
                currentMessage.id === optimisticMessageId ? message : currentMessage
              );
              if (message.processingStatus === 'PROCESSING') {
                this.pollMessages(conversationId);
              }
            } else {
              this.chatMessages = [...this.chatMessages, message];
            }
          }
          this.refreshConversationTitle(conversationId, content);
        },
        error: () => {
          if (this.selectedConversationId === conversationId) {
            this.chatErrorMessage = 'The local AI could not respond. Check that Ollama and its model are ready, then try again.';
          }
        }
      });
  }

  private pollMessages(conversationId: string): void {
    this.stopPollingMessages();
    this.chatPollingSubscription = timer(1500, 2000).pipe(
      switchMap(() => this.chatService.getMessages(conversationId).pipe(
        catchError(() => {
          if (this.selectedConversationId === conversationId) {
            this.chatErrorMessage = 'Could not refresh the assistant response. Retrying...';
          }
          return EMPTY;
        })
      )),
      tap(messages => {
        if (this.selectedConversationId === conversationId) {
          this.chatMessages = messages;
          if (!messages.some(message => message.processingStatus === 'PROCESSING')) {
            this.chatErrorMessage = '';
          }
        }
      }),
      takeWhile(messages => messages.some(message => message.processingStatus === 'PROCESSING'), true)
    ).subscribe();
  }

  private stopPollingMessages(): void {
    this.chatPollingSubscription?.unsubscribe();
    this.chatPollingSubscription = null;
  }

  resolveAction(message: ChatMessage, action: ChatAction, confirm: boolean): void {
    if (action.status !== 'PENDING' || this.resolvingActionIds.has(action.id)) {
      return;
    }

    this.resolvingActionIds.add(action.id);
    this.chatErrorMessage = '';
    const request = confirm
      ? this.chatService.confirmAction(action.id)
      : this.chatService.rejectAction(action.id);

    request.pipe(finalize(() => this.resolvingActionIds.delete(action.id))).subscribe({
      next: result => {
        action.status = result.status;
        if (confirm) {
          this.loadTasks();
          if (action.type === 'DELETE' && this.editingTask?.id === action.taskId) {
            this.cancelEditing();
          }
        }
        this.chatMessages = this.chatMessages.map(currentMessage => ({
          ...currentMessage,
          actions: currentMessage.actions.map(currentAction =>
            currentAction.id === action.id ? { ...currentAction, status: result.status } : currentAction
          )
        }));
      },
      error: () => this.chatErrorMessage = 'Could not apply this change. The task may have changed; ask the assistant to refresh the proposal.'
    });
  }

  private refreshConversationTitle(conversationId: string, firstMessage: string): void {
    this.conversations = this.conversations.map(conversation =>
      conversation.id === conversationId && conversation.title === 'New conversation'
        ? { ...conversation, title: firstMessage.length > 120 ? firstMessage.slice(0, 117) + '...' : firstMessage }
        : conversation
    );
  }

  onConversationChange(event: Event): void {
    const target = event.target;
    if (target instanceof HTMLSelectElement) {
      this.selectConversation(target.value);
    }
  }

  onChatKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendChatMessage();
    }
  }
}
