export interface Task {
  id: number;
  title: string;
  description: string | null;
  completed: boolean;
  createdAt: string;
}

export type TaskRequest = Pick<Task, 'title' | 'description' | 'completed'>;
