export interface NotificationTarget {
  commands: string[];
  queryParams?: Record<string, string | number>;
}

export function notificationTarget(item: { type?: string | null; entityType?: string | null; entityId?: number | null }): NotificationTarget | null {
  const type = item.type || '';
  const entity = item.entityType || '';
  if (type === 'NEW_MESSAGE' || entity === 'CONVERSATION') {
    return item.entityId
      ? { commands: ['/messages'], queryParams: { conversation: item.entityId } }
      : { commands: ['/messages'] };
  }
  if (entity === 'APPOINTMENT' || type.startsWith('APPOINTMENT')) {
    return { commands: ['/calendar'] };
  }
  return null;
}

export function relativeTime(value: string | null | undefined, instant: (key: string, params?: Record<string, unknown>) => string): string {
  if (!value) {
    return '';
  }
  const diff = Date.now() - new Date(value).getTime();
  if (!Number.isFinite(diff)) {
    return '';
  }
  const minutes = Math.max(0, Math.floor(diff / 60000));
  if (minutes < 1) {
    return instant('notifications.justNow');
  }
  if (minutes < 60) {
    return instant('notifications.minutesAgo', { count: minutes });
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return instant('notifications.hoursAgo', { count: hours });
  }
  return instant('notifications.daysAgo', { count: Math.floor(hours / 24) });
}
