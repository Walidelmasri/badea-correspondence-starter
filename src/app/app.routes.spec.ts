import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';
import { MAIN_NAVIGATION } from './core/navigation/navigation.config';

describe('default correspondence navigation', () => {
  const protectedChildren = routes.find((route) => route.path === '')?.children;

  it('opens Dashboard at the site root', () => {
    const root = protectedChildren?.find((route) => route.path === '');
    expect(root?.pathMatch).toBe('full');
    expect(root?.redirectTo).toBe('dashboard');
  });

  it('removes only the standalone President Review demo', () => {
    const demo = protectedChildren?.find(route => route.path === 'president-review');
    const live = protectedChildren?.find(route => route.path === 'president-review/:technicalId');
    expect(demo?.redirectTo).toBe('inbox');
    expect(live?.loadComponent).toBeDefined();
    expect(MAIN_NAVIGATION.some(item => item.route === '/president-review')).toBe(false);
  });

  it('exposes the public access-denied page without protecting it behind login', () => {
    const denied = routes.find(route => route.path === 'access-denied');
    expect(denied?.loadComponent).toBeDefined();
  });

  it('sends unknown app paths to Dashboard while keeping Inbox directly available', () => {
    const fallback = protectedChildren?.find((route) => route.path === '**');
    expect(fallback?.redirectTo).toBe('dashboard');
    expect(protectedChildren?.some((route) => route.path === 'inbox')).toBe(true);
  });
});
