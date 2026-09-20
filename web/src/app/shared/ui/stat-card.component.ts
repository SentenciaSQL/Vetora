import { Component, input } from '@angular/core';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  template: `
    <div class="card">
      <p class="text-sm text-slate-500">{{ label() }}</p>
      <p class="mt-2 font-display text-3xl font-semibold text-slate-900 dark:text-white">{{ value() }}</p>
      @if (hint()) {
        <p class="mt-1 text-xs text-slate-400">{{ hint() }}</p>
      }
    </div>
  `
})
export class StatCardComponent {
  label = input.required<string>();
  value = input<string | number>('');
  hint = input('');
}
