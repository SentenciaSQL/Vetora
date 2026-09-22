import { Component, input } from '@angular/core';

@Component({
  selector: 'app-user-avatar',
  standalone: true,
  template: `
    @if (url()) {
      <img [src]="url()!" [alt]="name() || ''" class="h-full w-full object-cover" />
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

  letters(): string {
    const parts = (this.name() || '').trim().split(/\s+/).filter(Boolean);
    const value = parts.slice(0, 2).map(part => part.charAt(0).toUpperCase()).join('');
    return value || '?';
  }
}
