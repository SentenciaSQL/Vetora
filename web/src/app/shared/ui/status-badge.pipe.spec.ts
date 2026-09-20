import { StatusBadgePipe } from './status-badge.pipe';

describe('StatusBadgePipe', () => {
  it('maps appointment statuses to badge classes', () => {
    const pipe = new StatusBadgePipe();
    expect(pipe.transform('CONFIRMED')).toContain('badge');
    expect(pipe.transform('OVERDUE')).toContain('rose');
  });
});
