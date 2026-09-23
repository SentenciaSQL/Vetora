import { Component, input, linkedSignal } from '@angular/core';

@Component({
  selector: 'app-user-avatar',
  standalone: true,
  template: `
    @if (showImage()) {
      <img [src]="url()!" [alt]="name() || ''" class="h-full w-full object-cover" (error)="failed.set(true)" />
    } @else {
      <span class="text-xs font-semibold tracking-wide">{{ letters() }}</span>
    }
  `,
  host: {
    class: 'grid shrink-0 place-items-center overflow-hidden rounded-full bg-brand-100 text-brand-800 dark:bg-brand-900/50 dark:text-brand-100'
  }
})
export class UserAvatarComponent {
  url = input<string | null | undefined>(null);
  name = input<string | null | undefined>('');
  failed = linkedSignal(() => {
    this.url();
    return false;
  });

  showImage(): boolean {
    return !!this.url() && !this.failed();
  }

  letters(): string {
    const parts = (this.name() || '').trim().split(/\s+/).filter(Boolean);
    const value = parts.slice(0, 2).map(part => part.charAt(0).toUpperCase()).join('');
    return value || '?';
  }
}
