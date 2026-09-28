import { AppLanguage } from '../../../core/i18n/language.service';
import { CorrespondenceInstruction } from '../domain/correspondence-instruction.model';

export const CORRESPONDENCE_INSTRUCTION_LABELS = {
  ar: {
    'required-action': 'لإجراء اللازم وفق النظام',
    'study-and-opinion': 'للدراسة وإبداء الرأي',
    'review-and-advise': 'للمراجعة والإفادة',
    'discuss-directly': 'للتفاهم / هاتفياً / شخصياً',
    'direct-response': 'للإجابة المباشرة',
    'prepare-summary': 'لإعداد خلاصة',
    'information-and-action': 'للعلم وإجراء اللازم',
    'follow-up': 'للمتابعة',
    'information-and-file': 'للعلم والحفظ',
    'act-per-phone-call': 'للعمل حسب المكالمة الهاتفية',
    'prepare-response': 'لإعداد رد مناسب',
    'apology': 'للاعتذار',
    'return-later': 'لإعادتها لي لاحقاً',
  },

  en: {
    'required-action': 'For necessary action in accordance with the system',
    'study-and-opinion': 'For study and opinion',
    'review-and-advise': 'For review and feedback',
    'discuss-directly': 'For discussion by phone / in person',
    'direct-response': 'For direct response',
    'prepare-summary': 'To prepare a summary',
    'information-and-action': 'For information and necessary action',
    'follow-up': 'For follow-up',
    'information-and-file': 'For information and filing',
    'act-per-phone-call': 'To act as per the phone call',
    'prepare-response': 'To prepare an appropriate response',
    'apology': 'To apologize',
    'return-later': 'To return to me later',
  },
} as const satisfies Record<
  AppLanguage,
  Record<CorrespondenceInstruction, string>
>;