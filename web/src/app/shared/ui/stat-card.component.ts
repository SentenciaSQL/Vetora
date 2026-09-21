import { Component, input } from '@angular/core';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  template: `
    <div class="card" [attr.title]="tooltip() || null">
      <div class="flex items-start justify-between gap-3">
        <p class="text-sm text-slate-500">{{ label() }}</p>
        @if (tooltip()) {
          <span class="grid h-6 w-6 place-items-center rounded-full bg-slate-100 text-xs text-slate-500 dark:bg-white/10" [title]="tooltip()">?</span>
        }
      </div>
      @if (loading()) {
        <div class="mt-3 h-9 w-24 animate-pulse rounded-lg bg-slate-100 dark:bg-white/10"></div>
      } @else if (error()) {
        <p class="mt-2 text-sm text-rose-600">{{ errorText() }}</p>
      } @else if (empty()) {
        <p class="mt-2 text-sm text-slate-400">{{ emptyText() }}</p>
      } @else {
        <p class="mt-2 font-display text-3xl font-semibold text-slate-900 dark:text-white">
          {{ value() }}@if (unit()) { <span class="text-base font-medium text-slate-400">{{ unit() }}</span> }
        </p>
      }
      @if (hint()) {
        <p class="mt-1 text-xs text-slate-400">{{ hint() }}</p>
      }
      @if (changePercent() !== null && changePercent() !== undefined) {
        <p class="mt-1 text-xs font-medium"
           [class.text-emerald-700]="trend() === 'up'"
           [class.text-rose-600]="trend() === 'down'"
           [class.text-slate-400]="trend() === 'neutral' || !trend()">
          {{ changePercent()! > 0 ? '+' : '' }}{{ changePercent() }}%
        </p>
      }
    </div>
  `
})
export class StatCardComponent {
  label = input.required<string>();
  value = input<string | number>('');
  hint = input('');
  tooltip = input('');
  trend = input('');
  changePercent = input<number | null>(null);
  loading = input(false);
  error = input(false);
  errorText = input('');
  empty = input(false);
  emptyText = input('');
  unit = input('');
}
