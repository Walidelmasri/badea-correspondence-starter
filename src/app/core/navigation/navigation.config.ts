import { NavigationItem } from './navigation.model';

export const MAIN_NAVIGATION: readonly NavigationItem[] = [
  {
    key: 'inbox',
    route: '/inbox',
  },
  {
    key: 'sent',
    route: '/sent',
  },
  {
    key: 'drafts',
    route: '/drafts',
  },
  {
    key: 'completed',
    route: '/completed',
  },
] as const;