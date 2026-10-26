import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';

describe('default correspondence navigation', () => {
  const protectedChildren = routes.find((route) => route.path === '')?.children;

  it('opens Dashboard at the site root', () => {
    const root = protectedChildren?.find((route) => route.path === '');
    expect(root?.pathMatch).toBe('full');
    expect(root?.redirectTo).toBe('dashboard');
  });

  it('sends unknown app paths to Dashboard while keeping Inbox directly available', () => {
    const fallback = protectedChildren?.find((route) => route.path === '**');
    expect(fallback?.redirectTo).toBe('dashboard');
    expect(protectedChildren?.some((route) => route.path === 'inbox')).toBe(true);
  });
});
