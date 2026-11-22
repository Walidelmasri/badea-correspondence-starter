import { describe, expect, it } from 'vitest';
import { mailboxSenderName } from './mailbox-sender-name';

describe('mailboxSenderName', () => {
  const sender = { senderName: 'English Name', senderNameArabic: 'الاسم العربي' };
  it('uses stored Arabic name for Arabic display', () => {
    expect(mailboxSenderName(sender, 'ar')).toBe('الاسم العربي');
  });
  it('preserves English display', () => {
    expect(mailboxSenderName(sender, 'en')).toBe('English Name');
  });
  it('falls back to English if Arabic is missing or whitespace', () => {
    expect(mailboxSenderName({ senderName: 'English Name' }, 'ar')).toBe('English Name');
    expect(mailboxSenderName({ senderName: 'English Name', senderNameArabic: '  ' }, 'ar')).toBe('English Name');
  });
});
