import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { finalize, interval, Subscription } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiService } from './api.service';
import { UnreadCount } from '../models';

const POLL_MS = 45_000;

@Injectable({ providedIn: 'root' })
export class MessageInboxService implements OnDestroy {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  unreadMessages = signal(0);
  unreadNotifications = signal(0);

  private polling?: Subscription;
  private inFlight = false;

  start(): void {
    this.stop();
    if (!this.auth.isAuthenticated) {
      this.unreadMessages.set(0);
      this.unreadNotifications.set(0);
      return;
    }
    this.refresh();
    this.polling = interval(POLL_MS).subscribe(() => this.refresh());
  }

  stop(): void {
    this.polling?.unsubscribe();
    this.polling = undefined;
    this.inFlight = false;
  }

  refresh(): void {
    if (!this.auth.isAuthenticated) {
      this.stop();
      this.unreadMessages.set(0);
      this.unreadNotifications.set(0);
      return;
    }
    if (this.inFlight) {
      return;
    }
    this.inFlight = true;
    this.api.get<UnreadCount>('/messages/unread-count').pipe(
      finalize(() => {
        this.inFlight = false;
      })
    ).subscribe({
      next: response => this.unreadMessages.set(response.count || 0),
      error: () => undefined
    });
    this.api.get<UnreadCount>('/notifications/unread-count').subscribe({
      next: response => this.unreadNotifications.set(response.count || 0),
      error: () => undefined
    });
  }

  setMessagesUnread(count: number): void {
    this.unreadMessages.set(Math.max(0, count));
  }

  badge(count: number): string {
    if (count > 99) {
      return '99+';
    }
    return String(count);
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
