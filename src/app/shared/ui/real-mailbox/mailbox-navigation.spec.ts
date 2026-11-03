import { describe, expect, it } from 'vitest';
import { composeRouteForRole, MAILBOX_PAGE_SIZE } from './mailbox-navigation';

describe('correspondence creation navigation', () => {
  it('opens the separate Director, Office and President creation pages', () => {
    expect(composeRouteForRole('DIRECTOR')).toBe('/director/new');
    expect(composeRouteForRole('PRESIDENT_OFFICE')).toBe('/office/new');
    expect(composeRouteForRole('PRESIDENT')).toBe('/compose');
  });
  it('never exposes the composer to an unrecognized role', () => {
    expect(composeRouteForRole('SECRETARY')).toBe('/inbox');
    expect(composeRouteForRole(null)).toBe('/inbox');
  });
  it('retains ten-item mailbox pagination', () => {
    expect(MAILBOX_PAGE_SIZE).toBe(10);
  });
});
