import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { ChatAction, ChatConversation, ChatMessage } from './chat.model';
import { Task } from './task.model';

describe('AppComponent', () => {
  let httpTestingController: HttpTestingController;

  const task: Task = {
    id: 1,
    title: 'Plan the week',
    description: 'Choose the important tasks',
    completed: false,
    createdAt: '2026-10-24T09:00:00Z'
  };

  const conversation: ChatConversation = {
    id: 'a1f289fe-452d-4d39-9578-305d0820db8a',
    title: 'New conversation',
    createdAt: '2026-10-24T09:00:00Z',
    updatedAt: '2026-10-24T09:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('loads and displays tasks from the API', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, [task]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Plan the week');
    expect(fixture.nativeElement.textContent).toContain('Choose the important tasks');
  });

  it('creates a task and clears the form after success', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, []);

    const component = fixture.componentInstance;
    component.taskForm.setValue({
      title: 'Write tests',
      description: '',
      completed: false
    });
    component.saveTask();

    const request = httpTestingController.expectOne('/api/tasks');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Write tests',
      description: null,
      completed: false
    });
    request.flush({ ...task, title: 'Write tests', description: null });

    expect(component.tasks).toHaveSize(1);
    expect(component.taskForm.controls.title.value).toBe('');
  });

  it('updates a task from the edit form', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, [task]);

    const component = fixture.componentInstance;
    component.startEditing(task);
    component.taskForm.controls.title.setValue('Plan the next sprint');
    component.saveTask();

    const request = httpTestingController.expectOne('/api/tasks/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.title).toBe('Plan the next sprint');
    request.flush({ ...task, title: 'Plan the next sprint' });

    expect(component.tasks[0].title).toBe('Plan the next sprint');
    expect(component.editingTask).toBeNull();
  });

  it('sends chat messages to the selected conversation', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, []);

    const component = fixture.componentInstance;
    component.chatForm.controls.content.setValue('What should I focus on?');
    component.sendChatMessage();

    const request = httpTestingController.expectOne(
      `/api/chat/conversations/${conversation.id}/messages`
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body.content).toBe('What should I focus on?');
    const answer: ChatMessage = {
      id: 1,
      role: 'assistant',
      content: 'Start with the most important task.',
      createdAt: '2026-10-24T09:01:00Z',
      actions: []
    };
    request.flush(answer);

    expect(component.chatMessages.map(message => message.content)).toEqual([
      'What should I focus on?',
      'Start with the most important task.'
    ]);
  });

  it('applies a proposed task change only after confirmation', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, []);

    const component = fixture.componentInstance;
    const action: ChatAction = {
      id: 'action-1',
      type: 'CREATE',
      status: 'PENDING',
      taskId: null,
      title: 'Prepare a demo',
      description: null,
      completed: false,
      createdAt: '2026-10-24T09:01:00Z'
    };
    const message: ChatMessage = {
      id: 2,
      role: 'assistant',
      content: 'I can add that task.',
      createdAt: '2026-10-24T09:01:00Z',
      actions: [action]
    };
    component.chatMessages = [message];
    component.resolveAction(message, action, true);

    const request = httpTestingController.expectOne('/api/chat/actions/action-1/confirm');
    expect(request.request.method).toBe('POST');
    request.flush({ actionId: 'action-1', status: 'APPLIED', task });
    httpTestingController.expectOne('/api/tasks').flush([task]);

    expect(component.chatMessages[0].actions[0].status).toBe('APPLIED');
    expect(component.tasks).toEqual([task]);
  });

  it('deletes the selected conversation only after confirmation', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, []);
    const component = fixture.componentInstance;
    const confirmSpy = spyOn(globalThis, 'confirm').and.returnValue(true);

    component.deleteSelectedConversation();

    expect(confirmSpy).toHaveBeenCalled();
    const request = httpTestingController.expectOne(
      `/api/chat/conversations/${conversation.id}`
    );
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    const createRequest = httpTestingController.expectOne('/api/chat/conversations');
    expect(createRequest.request.method).toBe('POST');
    createRequest.flush({ ...conversation, id: 'next-conversation' });

    expect(component.conversations.map(item => item.id)).toEqual(['next-conversation']);
    expect(component.selectedConversationId).toBe('next-conversation');
    expect(component.chatMessages).toEqual([]);
  });

  it('does not delete a conversation when confirmation is declined', () => {
    const fixture = TestBed.createComponent(AppComponent);
    startApp(fixture, []);
    spyOn(globalThis, 'confirm').and.returnValue(false);

    fixture.componentInstance.deleteSelectedConversation();

    httpTestingController.expectNone(
      `/api/chat/conversations/${conversation.id}`
    );
    expect(fixture.componentInstance.selectedConversationId).toBe(conversation.id);
  });

  function startApp(
    fixture: ComponentFixture<AppComponent>,
    tasks: Task[]
  ): void {
    fixture.detectChanges();
    httpTestingController.expectOne('/api/tasks').flush(tasks);
    httpTestingController.expectOne('/api/chat/conversations').flush([conversation]);
    httpTestingController
      .expectOne(`/api/chat/conversations/${conversation.id}/messages`)
      .flush([]);
  }
});
