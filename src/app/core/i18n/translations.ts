import { AppLanguage } from './language.service';

export const TRANSLATIONS = {
  ar: {
    app: {
      name: 'مراسلات الرئاسة',
      nameEnglish: 'Presidency Correspondence',
    },

    navigation: {
      inbox: 'الوارد',
      sent: 'المرسل',
      drafts: 'المسودات',
      completed: 'المكتمل',
    },

    inbox: {
      eyebrow: 'مساحة العمل',
      title: 'الوارد',
      description: 'المراسلات والوثائق الواردة إليك',
      newCorrespondence: 'مراسلة جديدة',

      search: {
        label: 'البحث في المراسلات',
        placeholder: 'بحث بالموضوع، المرسل أو رقم الوثيقة',
      },

      empty: {
        title: 'لا توجد مراسلات حالياً',
        description: 'ستظهر المراسلات الواردة هنا.',
      },

      detailPlaceholder: {
        title: 'اختر مراسلة لعرضها',
        description: 'ستظهر تفاصيل الوثيقة والإجراء المطلوب هنا.',
      },
    },

    user: {
      office: 'مكتب الرئيس',
    },
  },

  en: {
    app: {
      name: 'Presidency Correspondence',
      nameEnglish: 'Presidency Correspondence',
    },

    navigation: {
      inbox: 'Inbox',
      sent: 'Sent',
      drafts: 'Drafts',
      completed: 'Completed',
    },

    inbox: {
      eyebrow: 'Workspace',
      title: 'Inbox',
      description: 'Correspondence and documents received by you',
      newCorrespondence: 'New Correspondence',

      search: {
        label: 'Search correspondence',
        placeholder: 'Search by subject, sender or document number',
      },

      empty: {
        title: 'No correspondence yet',
        description: 'Incoming correspondence will appear here.',
      },

      detailPlaceholder: {
        title: 'Select correspondence to view',
        description:
          'Document details and the required action will appear here.',
      },
    },

    user: {
      office: 'President Office',
    },
  },
} as const satisfies Record<AppLanguage, object>;