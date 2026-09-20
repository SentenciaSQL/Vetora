import { Component, input } from '@angular/core';

@Component({
  selector: 'empty-state',
  standalone: true,
  template: `
    <div class="px-6 py-12 text-center">
      <div class="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200">
        <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6v6l4 2M4.5 12a7.5 7.5 0 1115 0 7.5 7.5 0 01-15 0z" />
        </svg>
      </div>
      <p class="font-medium text-slate-700 dark:text-slate-200">{{ title() }}</p>
      @if (subtitle()) {
        <p class="mt-1 text-sm text-slate-500">{{ subtitle() }}</p>
      }
    </div>
  `
})
export class EmptyStateComponent {
  title = input.required<string>();
  subtitle = input('');
}
