import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatActionResult, ChatConversation, ChatMessage } from './chat.model';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/chat';

  getConversations(): Observable<ChatConversation[]> {
    return this.http.get<ChatConversation[]>(`${this.apiUrl}/conversations`);
  }

  createConversation(): Observable<ChatConversation> {
    return this.http.post<ChatConversation>(`${this.apiUrl}/conversations`, {});
  }

  deleteConversation(conversationId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/conversations/${conversationId}`);
  }

  getMessages(conversationId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.apiUrl}/conversations/${conversationId}/messages`);
  }

  sendMessage(conversationId: string, content: string): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.apiUrl}/conversations/${conversationId}/messages`, { content });
  }

  confirmAction(actionId: string): Observable<ChatActionResult> {
    return this.http.post<ChatActionResult>(`${this.apiUrl}/actions/${actionId}/confirm`, {});
  }

  rejectAction(actionId: string): Observable<ChatActionResult> {
    return this.http.post<ChatActionResult>(`${this.apiUrl}/actions/${actionId}/reject`, {});
  }
}
