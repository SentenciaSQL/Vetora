import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'statusBadge', standalone: true })
export class StatusBadgePipe implements PipeTransform {
  transform(status: string | null | undefined): string {
    const map: Record<string, string> = {
      REQUESTED: 'bg-sky-50 text-sky-800 dark:bg-sky-500/10 dark:text-sky-200',
      PENDING: 'bg-amber-50 text-amber-800 dark:bg-amber-500/10 dark:text-amber-200',
      CONFIRMED: 'bg-emerald-50 text-emerald-800 dark:bg-emerald-500/10 dark:text-emerald-200',
      ARRIVED: 'bg-teal-50 text-teal-800 dark:bg-teal-500/10 dark:text-teal-200',
      WAITING: 'bg-indigo-50 text-indigo-800 dark:bg-indigo-500/10 dark:text-indigo-200',
      IN_PROGRESS: 'bg-brand-50 text-brand-800 dark:bg-brand-500/10 dark:text-brand-100',
      COMPLETED: 'bg-slate-100 text-slate-700 dark:bg-white/10 dark:text-slate-200',
      CANCELLED: 'bg-rose-50 text-rose-700 dark:bg-rose-500/10 dark:text-rose-200',
      NO_SHOW: 'bg-orange-50 text-orange-800 dark:bg-orange-500/10 dark:text-orange-200',
      ACTIVE: 'bg-emerald-50 text-emerald-800',
      TRIAL: 'bg-sky-50 text-sky-800',
      SUSPENDED: 'bg-rose-50 text-rose-700',
      UP_TO_DATE: 'bg-emerald-50 text-emerald-800',
      DUE_SOON: 'bg-amber-50 text-amber-800',
      OVERDUE: 'bg-rose-50 text-rose-700'
    };
    return `badge ${map[status || ''] || 'bg-slate-100 text-slate-600'}`;
  }
}
