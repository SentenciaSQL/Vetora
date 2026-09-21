export function apiErrorMessage(error: unknown, fallback = 'No se pudo completar la acción'): string {
  const response = error as { error?: unknown; message?: unknown };
  const body = response?.error;
  if (typeof body === 'string' && body.trim() && isSafeClientMessage(body)) {
    return body.trim();
  }
  if (body && typeof body === 'object') {
    const message = (body as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim() && isSafeClientMessage(message)) {
      return message.trim();
    }
  }
  if (typeof response?.message === 'string' && response.message.trim() && isSafeClientMessage(response.message)
      && !response.message.startsWith('Http failure response')) {
    return response.message.trim();
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
