import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { interval, Subscription } from 'rxjs';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { MessageInboxService } from '../../../core/services/message-inbox.service';
import { ToastService } from '../../../core/services/toast.service';
import { ChatMessage, ConversationSummary, PageResponse } from '../../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'messages.title' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ 'messages.disclaimer' | translate }}</p>
    <div class="mt-6 grid gap-4 lg:grid-cols-3">
      <div class="card space-y-2 p-2">
        @if (auth.isStaff()) {
          <button type="button" class="btn-secondary w-full text-sm" (click)="compose=true">{{ 'messages.new' | translate }}</button>
        }
        @if (listError()) {
          <p class="px-3 py-2 text-sm text-rose-600">{{ listError() }}</p>
        }
        @if (!listError() && convos().length === 0) {
          <p class="px-3 py-6 text-center text-sm text-slate-500">{{ 'messages.noConversations' | translate }}</p>
        }
        @for (c of convos(); track c.id) {
          <button type="button"
                  class="w-full rounded-xl px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-white/5"
                  [class]="current?.id === c.id ? 'bg-brand-50 ring-1 ring-brand-200 dark:bg-brand-900/40' : ''"
                  (click)="select(c)">
            <div class="flex items-start justify-between gap-2">
              <p class="flex min-w-0 items-center gap-2 truncate text-sm" [class.font-semibold]="c.unread > 0">
                @if (c.unread > 0) {
                  <span class="h-2 w-2 shrink-0 rounded-full bg-rose-600" aria-hidden="true"></span>
                }
                <span class="truncate">{{ displayTitle(c) }}</span>
              </p>
              @if (c.unread > 0) {
                <span class="grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] text-white"
                      [attr.aria-label]="unreadLabel(c.unread)">{{ inbox.badge(c.unread) }}</span>
              }
            </div>
            <div class="mt-0.5 flex items-center justify-between gap-2">
              <p class="truncate text-xs" [class.font-semibold]="c.unread > 0" [class.text-slate-500]="c.unread === 0">
                {{ c.lastMessage || ('messages.noPreview' | translate) }}
              </p>
              <p class="shrink-0 text-[11px] text-slate-400">{{ c.updatedAt | date:'short' }}</p>
            </div>
          </button>
        }
      </div>
      <div class="card flex min-h-96 flex-col lg:col-span-2">
        @if (!current) {
          <p class="m-auto text-sm text-slate-500">{{ 'messages.empty' | translate }}</p>
        } @else if (loading()) {
          <p class="m-auto text-sm text-slate-500">{{ 'messages.loading' | translate }}</p>
        } @else if (chatError()) {
          <div class="m-auto max-w-sm space-y-2 text-center">
            <p class="text-sm text-rose-600">{{ chatError() }}</p>
            <button type="button" class="btn-secondary" (click)="select(current)">{{ 'messages.retry' | translate }}</button>
          </div>
        } @else {
          <div class="flex-1 space-y-2 overflow-y-auto" #thread>
            @if (hasOlder()) {
              <button type="button" class="mx-auto block text-xs text-brand-700" (click)="loadOlder()">
                {{ 'messages.loadOlder' | translate }}
              </button>
            }
            @if (messages().length === 0) {
              <p class="py-8 text-center text-sm text-slate-500">{{ 'messages.threadEmpty' | translate }}</p>
            }
            @for (m of messages(); track m.id) {
              <div class="rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-white/5"
                   [class.ml-8]="m.senderId === auth.user()?.id"
                   [class.mr-8]="m.senderId !== auth.user()?.id">
                <p class="text-xs text-slate-400">{{ m.senderName }} · {{ m.createdAt | date:'short' }}</p>
                <p class="whitespace-pre-wrap break-words">{{ m.body }}</p>
              </div>
            }
          </div>
          <form class="mt-3 flex gap-2" (ngSubmit)="send()">
            <input class="input" [(ngModel)]="draft" name="draft" [disabled]="sending()"
                   [placeholder]="'messages.placeholder' | translate" autocomplete="off" />
            <button class="btn-primary" [disabled]="sending() || !draft.trim()">{{ 'messages.send' | translate }}</button>
          </form>
        }
      </div>
    </div>
    @if (compose) {
      <div class="fixed inset-0 z-50 grid place-items-center bg-black/40 p-4" (click)="compose=false">
        <form class="card w-full max-w-md space-y-3" (click)="$event.stopPropagation()" (ngSubmit)="start()">
          <h2 class="font-display text-lg">{{ 'messages.new' | translate }}</h2>
          <select class="input" [(ngModel)]="ownerId" name="ownerId">
            @for (o of owners(); track o.id) { <option [value]="o.id">{{ o.fullName }}</option> }
          </select>
          <input class="input" [(ngModel)]="subject" name="subject" [placeholder]="'messages.subject' | translate" />
          <div class="flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="compose=false">{{ 'common.cancel' | translate }}</button>
            <button class="btn-primary">{{ 'common.create' | translate }}</button>
          </div>
        </form>
      </div>
    }
  `
})
export class MessagesPage implements OnInit, OnDestroy {
  private api = inject(ApiService);
  auth = inject(AuthService);
  inbox = inject(MessageInboxService);
  private toast = inject(ToastService);
  private i18n = inject(TranslateService);
  private route = inject(ActivatedRoute);

  convos = signal<ConversationSummary[]>([]);
  messages = signal<ChatMessage[]>([]);
  current?: ConversationSummary;
  draft = '';
  compose = false;
  owners = signal<any[]>([]);
  ownerId = '';
  subject = '';
  loading = signal(false);
  sending = signal(false);
  listError = signal('');
  chatError = signal('');
  private poll?: Subscription;
  private querySub?: Subscription;
  private loadedTotal = 0;
  private currentPage = 0;

  ngOnInit() {
    this.loadConversations();
    if (this.auth.isStaff()) {
      this.api.get<PageResponse<any>>('/owners', { size: 100 }).subscribe({
        next: r => this.owners.set(r.content || []),
        error: () => undefined
      });
    }
    this.querySub = this.route.queryParamMap.subscribe(params => {
      const id = Number(params.get('conversation'));
      if (id && this.current?.id !== id) {
        this.openById(id);
      }
    });
    this.poll = interval(45_000).subscribe(() => this.syncOpen());
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
    this.querySub?.unsubscribe();
  }

  hasOlder(): boolean {
    return this.loadedTotal > this.messages().length;
  }

  displayTitle(conversation: ConversationSummary): string {
    return conversation.title || conversation.ownerName || conversation.tenantName || conversation.subject || this.i18n.instant('common.conversation');
  }

  unreadLabel(count: number): string {
    return this.i18n.instant('messages.unreadAria', { count });
  }

  select(conversation: ConversationSummary): void {
    this.current = conversation;
    this.chatError.set('');
    this.loading.set(true);
    this.currentPage = 0;
    this.api.get<PageResponse<ChatMessage>>(`/messages/${conversation.id}`, { size: 50, page: 0 }).subscribe({
      next: page => {
        this.messages.set(page.content || []);
        this.loadedTotal = page.totalElements || (page.content || []).length;
        this.loading.set(false);
        this.api.post<ConversationSummary>(`/messages/${conversation.id}/read`, {}).subscribe({
          next: updated => this.applyRead(updated),
          error: () => this.inbox.refresh()
        });
      },
      error: err => {
        this.loading.set(false);
        this.messages.set([]);
        if (err?.status === 403) {
          this.chatError.set(this.i18n.instant('messages.forbidden'));
          return;
        }
        this.chatError.set(err?.error?.message || this.i18n.instant('messages.loadError'));
      }
    });
  }

  loadOlder(): void {
    if (!this.current || this.loading() || !this.hasOlder()) {
      return;
    }
    const nextPage = this.currentPage + 1;
    this.api.get<PageResponse<ChatMessage>>(`/messages/${this.current.id}`, { size: 50, page: nextPage }).subscribe({
      next: page => {
        const older = page.content || [];
        this.currentPage = nextPage;
        this.messages.update(list => [...older.filter(item => list.every(existing => existing.id !== item.id)), ...list]);
        this.loadedTotal = page.totalElements || this.loadedTotal;
      },
      error: err => this.toast.showHttpError(err, 'messages.loadError')
    });
  }

  send() {
    if (!this.current || !this.draft.trim() || this.sending()) {
      return;
    }
    const body = this.draft.trim();
    this.sending.set(true);
    this.api.post<ChatMessage>(`/messages/${this.current.id}`, { body }).subscribe({
      next: message => {
        this.draft = '';
        this.sending.set(false);
        this.messages.update(list => [...list, message]);
        const preview = body.length > 80 ? `${body.slice(0, 80)}…` : body;
        this.convos.update(list => {
          const next = list.map(item => item.id === this.current?.id
            ? { ...item, lastMessage: preview, updatedAt: message.createdAt, unread: 0 }
            : item);
          return next.sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')));
        });
        if (this.current) {
          this.current = { ...this.current, lastMessage: preview, updatedAt: message.createdAt, unread: 0 };
        }
        this.inbox.refresh();
      },
      error: err => {
        this.sending.set(false);
        this.toast.showHttpError(err, 'messages.sendError');
      }
    });
  }

  start() {
    if (!this.ownerId) {
      return;
    }
    this.api.post<ConversationSummary>('/messages', { ownerId: Number(this.ownerId), subject: this.subject }).subscribe({
      next: conversation => {
        this.compose = false;
        this.subject = '';
        this.loadConversations();
        this.select(conversation);
      },
      error: err => this.toast.showHttpError(err, 'messages.sendError')
    });
  }

  private loadConversations(): void {
    this.api.get<ConversationSummary[]>('/messages').subscribe({
      next: conversations => {
        this.convos.set(conversations || []);
        this.listError.set('');
        this.inbox.setMessagesUnread((conversations || []).reduce((sum, item) => sum + (item.unread || 0), 0));
        if (this.current) {
          const match = (conversations || []).find(item => item.id === this.current?.id);
          if (match) {
            this.current = { ...this.current, ...match, unread: this.chatError() ? match.unread : 0 };
          }
        }
      },
      error: err => {
        this.listError.set(err?.status === 403
          ? this.i18n.instant('messages.forbidden')
          : this.i18n.instant('messages.loadError'));
      }
    });
  }

  private openById(id: number): void {
    const existing = this.convos().find(item => item.id === id);
    if (existing) {
      this.select(existing);
      return;
    }
    this.select({ id, unread: 0, title: '' });
  }

  private applyRead(updated: ConversationSummary): void {
    this.convos.update(list => list.map(item => item.id === updated.id ? { ...item, ...updated, unread: 0 } : item));
    if (this.current?.id === updated.id) {
      this.current = { ...this.current, ...updated, unread: 0 };
    }
    this.inbox.refresh();
  }

  private syncOpen(): void {
    this.loadConversations();
    const open = this.current;
    const last = this.messages().at(-1)?.id;
    if (!open || this.loading() || this.chatError()) {
      return;
    }
    this.api.get<PageResponse<ChatMessage>>(`/messages/${open.id}`, last ? { afterId: last } : { size: 50 }).subscribe({
      next: page => {
        const incoming = page.content || [];
        if (!last) {
          this.messages.set(incoming);
          return;
        }
        if (incoming.length) {
          this.messages.update(list => [...list, ...incoming.filter(item => list.every(existing => existing.id !== item.id))]);
        }
      },
      error: () => undefined
    });
  }
}
