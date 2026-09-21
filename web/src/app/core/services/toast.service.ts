import { Injectable, signal } from '@angular/core';
import { apiErrorMessage } from '../http-error';

export interface Toast {
  id: number;
  message: string;
  error: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  toasts = signal<Toast[]>([]);

  show(message: string, error = false): void {
    const toast: Toast = { id: ++this.seq, message, error };
    this.toasts.update(list => [...list, toast]);
    setTimeout(() => this.dismiss(toast.id), 4200);
  }

  showHttpError(error: unknown, fallback = 'common.error'): void {
    this.show(apiErrorMessage(error, fallback), true);
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(item => item.id !== id));
  }
}
