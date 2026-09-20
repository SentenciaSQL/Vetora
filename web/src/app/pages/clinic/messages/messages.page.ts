import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { ApiService } from '../../../core/services/api.service';
import { PageResponse } from '../../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    <h1 class="font-display text-2xl font-semibold">{{ 'messages.title' | translate }}</h1>
    <p class="mt-1 text-sm text-slate-500">{{ 'messages.disclaimer' | translate }}</p>
    <div class="mt-6 grid gap-4 lg:grid-cols-3">
      <div class="card space-y-2 p-2">
        <button type="button" class="btn-secondary w-full text-sm" (click)="compose=true">{{ 'messages.new' | translate }}</button>
        @for (c of convos(); track c.id) {
          <button type="button" class="w-full rounded-xl px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-white/5" (click)="select(c)">
            <p class="font-medium">{{ c.ownerName || c.subject || ('common.conversation' | translate) }}</p>
            <p class="truncate text-xs text-slate-500">{{ c.lastMessage }}</p>
          </button>
        }
      </div>
      <div class="card flex min-h-96 flex-col lg:col-span-2">
        <div class="flex-1 space-y-2 overflow-y-auto">
          @for (m of messages(); track m.id) {
            <div class="rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-white/5">
              <p class="text-xs text-slate-400">{{ m.senderName }} · {{ m.createdAt | date:'short' }}</p>
              {{ m.body }}
            </div>
          }
        </div>
        <form class="mt-3 flex gap-2" (ngSubmit)="send()">
          <input class="input" [(ngModel)]="draft" name="draft" />
          <button class="btn-primary">{{ 'messages.send' | translate }}</button>
        </form>
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
export class MessagesPage implements OnInit {
  private api = inject(ApiService);
  convos = signal<any[]>([]);
  messages = signal<any[]>([]);
  current?: any;
  draft = '';
  compose = false;
  owners = signal<any[]>([]);
  ownerId = '';
  subject = '';

  ngOnInit() {
    this.api.get<any[]>('/messages').subscribe(c => this.convos.set(c));
    this.api.get<PageResponse<any>>('/owners', { size: 100 }).subscribe(r => this.owners.set(r.content || []));
  }

  select(c: any) {
    this.current = c;
    this.api.get<any[]>(`/messages/${c.id}`).subscribe(m => this.messages.set(m));
  }

  send() {
    if (!this.current || !this.draft) return;
    this.api.post(`/messages/${this.current.id}`, { body: this.draft }).subscribe(() => {
      this.draft = '';
      this.select(this.current);
    });
  }

  start() {
    if (!this.ownerId) return;
    this.api.post('/messages', { ownerId: Number(this.ownerId), subject: this.subject }).subscribe((c: any) => {
      this.compose = false;
      this.ngOnInit();
      this.select(c);
    });
  }
}
