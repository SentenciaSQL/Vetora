export function apiErrorMessage(error: unknown, fallback = 'No se pudo completar la acción'): string {
  const body = (error as { error?: unknown })?.error;
  if (body && typeof body === 'object') {
    const message = (body as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim() && isSafeClientMessage(message)) {
      return message.trim();
    }
  }
  if (error instanceof Error && isSafeClientMessage(error.message)) {
    return error.message;
  }
  return fallback;
}

function isSafeClientMessage(message: string): boolean {
  const lower = message.toLowerCase();
  return !lower.includes('bearer ')
    && !lower.includes('api key')
    && !lower.includes('api-key')
    && !lower.includes('webhook')
    && !lower.includes('jwt')
    && !lower.includes('signature')
    && !/(pk_|sk_|pdl_|ctoken)/i.test(message);
}
