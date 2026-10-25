import { Task } from './task.model';

export interface ChatConversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export type ChatActionType = 'CREATE' | 'UPDATE' | 'DELETE';
export type ChatActionStatus = 'PENDING' | 'APPLIED' | 'REJECTED';

export interface ChatAction {
  id: string;
  type: ChatActionType;
  status: ChatActionStatus;
  taskId: number | null;
  title: string | null;
  description: string | null;
  completed: boolean | null;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
  actions: ChatAction[];
}

export interface ChatActionResult {
  actionId: string;
  status: ChatActionStatus;
  task: Task | null;
}
