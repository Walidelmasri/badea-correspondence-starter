import { AppLanguage } from './language.service';

type TranslationShape<T> = {
  [Key in keyof T]:
  T[Key] extends string
  ? string
  : TranslationShape<T[Key]>;
};

const AR_TRANSLATIONS = {
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

    detail: {
      sender: 'المرسل',
      department: 'الإدارة',
      requiredAction: 'الإجراء المطلوب',
      attachments: 'المرفقات',
      documentPreview: 'معاينة الوثيقة',
    },
  },
  sent: {
    eyebrow: 'مساحة العمل',
    title: 'المرسل',
    description: 'المراسلات والوثائق التي قمت بإرسالها',
    newCorrespondence: 'مراسلة جديدة',

    search: {
      label: 'البحث في المراسلات المرسلة',
      placeholder: 'بحث بالموضوع، المستلم أو رقم الوثيقة',
    },

    empty: {
      title: 'لا توجد مراسلات مرسلة',
      description: 'ستظهر المراسلات التي ترسلها هنا.',
    },

    detailPlaceholder: {
      title: 'اختر مراسلة لعرضها',
      description: 'ستظهر تفاصيل المراسلة والمستلمين هنا.',
    },
  },
  drafts: {
    eyebrow: 'مساحة العمل',
    title: 'المسودات',
    description: 'المراسلات التي لم يتم إرسالها بعد',
    newCorrespondence: 'مراسلة جديدة',

    search: {
      label: 'البحث في المسودات',
      placeholder: 'بحث بالموضوع أو رقم الوثيقة',
    },

    empty: {
      title: 'لا توجد مسودات',
      description: 'ستظهر المراسلات المحفوظة كمسودات هنا.',
    },

    detailPlaceholder: {
      title: 'اختر مسودة لعرضها',
      description: 'ستظهر تفاصيل المسودة هنا.',
    },
  },
  compose: {
    eyebrow: 'مراسلات الرئاسة',
    title: 'مراسلة جديدة',
    description: 'إعداد وإرسال مراسلة جديدة',

    fields: {
      recipient: 'إلى السيد / الجهة',
      subject: 'الموضوع',
      documentNumber: 'رقم الوثيقة',
      documentDate: 'تاريخ الوثيقة',
      entity: 'الدولة / الجهة / الإدارة',
      instruction: 'الإجراء المطلوب',
      explanation: 'الشرح',
      attachments: 'المرفقات',
      copyTo: 'صورة مع التحية',
    },

    placeholders: {
      recipient: 'اختر المستلم',
      subject: 'أدخل موضوع المراسلة',
      documentNumber: 'أدخل رقم الوثيقة',
      entity: 'اختر أو أدخل الدولة / الجهة / الإدارة',
      explanation: 'أدخل الشرح أو التوجيهات',
      copyTo: 'إضافة مستلم نسخة',
    },

    actions: {
      saveDraft: 'حفظ كمسودة',
      send: 'إرسال',
      cancel: 'إلغاء',
      addAttachment: 'إضافة مرفق',
    },
  },
  pdf: {
    toolbar: {
      highlight: 'تمييز',
      draw: 'رسم',
      text: 'نص',
      signature: 'توقيع',
      thumbnails: 'الصفحات',
      zoomIn: 'تكبير',
      zoomOut: 'تصغير',
      pageWidth: 'عرض الصفحة',
      undo: 'تراجع',
      redo: 'إعادة',
      more: 'المزيد',
      previousPage: 'الصفحة السابقة',
      nextPage: 'الصفحة التالية',
      search: 'بحث',
      rotate: 'تدوير',
      print: 'طباعة',
      download: 'تنزيل',
      properties: 'خصائص المستند',
      rotateClockwise: 'تدوير مع عقارب الساعة',
      rotateCounterclockwise: 'تدوير عكس عقارب الساعة',
    },
    properties: {
      heading: 'خصائص المستند',
      fileInformation: 'معلومات الملف',
      filename: 'اسم الملف',
      size: 'حجم الملف',
      pages: 'عدد الصفحات',
      documentDetails: 'تفاصيل المستند',
      title: 'العنوان',
      author: 'المؤلف',
      created: 'تاريخ الإنشاء',
      modified: 'آخر تعديل',
      close: 'إغلاق',
      unknown: 'غير متوفر',
    },
    saveStatus: {
      saving: 'جارٍ الحفظ...',
      saved: 'تم الحفظ',
      failed: 'تعذر الحفظ',
    },
  },
  user: {
    office: 'مكتب الرئيس',
  },
  presidentReview: {
    eyebrow: 'مساحة الرئيس',
    title: 'المراسلة والتوجيه',
    reference: 'المرجع',
    date: 'التاريخ',
    documents: 'المستندات',
    attachments: 'المرفقات',
    optional: 'اختياري',
    explanation: 'التوضيح',
    pen: 'قلم',
    keyboard: 'لوحة المفاتيح',
    writeWithPencil: 'اكتب باستخدام القلم',
    writeWithPencilHint: 'استخدم Apple Pencil لإضافة التوضيح',
    explanationPlaceholder: 'اكتب التوضيح هنا...',
    direction: 'التوجيه',
    yourDirection: 'توجيه الرئيس',
    directionHint: 'حدد الجهة والإجراء المطلوب',
    to: 'إلى',
    required: 'مطلوب',
    presidentOffice: 'مكتب الرئيس',
    defaultRecipient: 'محدد افتراضياً',
    requiredAction: 'الإجراء المطلوب',
    showAllActions: 'عرض جميع الإجراءات',
    showFewerActions: 'عرض أقل',
    otherRecipientPlaceholder: 'اكتب الجهة...',
    otherActionPlaceholder: 'اكتب الإجراء المطلوب...',
    copyTo: 'نسخة إلى',
    sendToOffice: 'إرسال إلى مكتب الرئيس',
  },
} as const;

const EN_TRANSLATIONS = {
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

    detail: {
      sender: 'Sender',
      department: 'Department',
      requiredAction: 'Required Action',
      attachments: 'Attachments',
      documentPreview: 'Document Preview',
    },
  },
  sent: {
    eyebrow: 'Workspace',
    title: 'Sent',
    description: 'Correspondence and documents you have sent',
    newCorrespondence: 'New Correspondence',

    search: {
      label: 'Search sent correspondence',
      placeholder: 'Search by subject, recipient or document number',
    },

    empty: {
      title: 'No sent correspondence',
      description: 'Correspondence you send will appear here.',
    },

    detailPlaceholder: {
      title: 'Select correspondence to view',
      description: 'Correspondence details and recipients will appear here.',
    },
  },
  drafts: {
    eyebrow: 'Workspace',
    title: 'Drafts',
    description: 'Correspondence that has not yet been sent',
    newCorrespondence: 'New Correspondence',

    search: {
      label: 'Search drafts',
      placeholder: 'Search by subject or document number',
    },

    empty: {
      title: 'No drafts',
      description: 'Correspondence saved as drafts will appear here.',
    },

    detailPlaceholder: {
      title: 'Select a draft to view',
      description: 'Draft details will appear here.',
    },
  },
  compose: {
    eyebrow: 'Presidency Correspondence',
    title: 'New Correspondence',
    description: 'Prepare and send new correspondence',

    fields: {
      recipient: 'To / Recipient',
      subject: 'Subject',
      documentNumber: 'Document Number',
      documentDate: 'Document Date',
      entity: 'Country / Entity / Department',
      instruction: 'Required Action',
      explanation: 'Explanation',
      attachments: 'Attachments',
      copyTo: 'Copy To',
    },

    placeholders: {
      recipient: 'Select recipient',
      subject: 'Enter correspondence subject',
      documentNumber: 'Enter document number',
      entity: 'Select or enter country / entity / department',
      explanation: 'Enter explanation or instructions',
      copyTo: 'Add copy recipient',
    },

    actions: {
      saveDraft: 'Save Draft',
      send: 'Send',
      cancel: 'Cancel',
      addAttachment: 'Add Attachment',
    },
  },
  pdf: {
    toolbar: {
      highlight: 'Highlight',
      draw: 'Draw',
      text: 'Text',
      signature: 'Signature',
      thumbnails: 'Pages',
      zoomIn: 'Zoom In',
      zoomOut: 'Zoom Out',
      pageWidth: 'Page Width',
      undo: 'Undo',
      redo: 'Redo',
      more: 'More',
      previousPage: 'Previous Page',
      nextPage: 'Next Page',
      search: 'Search',
      rotate: 'Rotate',
      print: 'Print',
      download: 'Download',
      properties: 'Document Properties',
      rotateClockwise: 'Rotate Clockwise',
      rotateCounterclockwise: 'Rotate Counterclockwise',
    },
    properties: {
      heading: 'Document Properties',
      fileInformation: 'File Information',
      filename: 'Filename',
      size: 'File Size',
      pages: 'Pages',
      documentDetails: 'Document Details',
      title: 'Title',
      author: 'Author',
      created: 'Created',
      modified: 'Last Modified',
      close: 'Close',
      unknown: 'Unavailable',
    },
    saveStatus: {
      saving: 'Saving...',
      saved: 'Saved',
      failed: 'Save failed',
    },
  },
  user: {
    office: 'President Office',
  },
  presidentReview: {
    eyebrow: 'President Workspace',
    title: 'Correspondence & Direction',
    reference: 'Reference',
    date: 'Date',
    documents: 'Documents',
    attachments: 'Attachments',
    optional: 'Optional',
    explanation: 'Explanation',
    pen: 'Pen',
    keyboard: 'Keyboard',
    writeWithPencil: 'Write with Apple Pencil',
    writeWithPencilHint: 'Use Apple Pencil to add an explanation',
    explanationPlaceholder: 'Type the explanation here...',
    direction: 'Direction',
    yourDirection: 'President Direction',
    directionHint: 'Select the recipient and required action',
    to: 'To',
    required: 'Required',
    presidentOffice: 'President Office',
    defaultRecipient: 'Selected by default',
    requiredAction: 'Required Action',
    showAllActions: 'Show all actions',
    showFewerActions: 'Show fewer',
    otherRecipientPlaceholder: 'Enter recipient...',
    otherActionPlaceholder: 'Enter required action...',
    copyTo: 'Copy To',
    sendToOffice: 'Send to President Office',
  },
} as const satisfies TranslationShape<typeof AR_TRANSLATIONS>;

export const TRANSLATIONS = {
  ar: AR_TRANSLATIONS,
  en: EN_TRANSLATIONS,
} as const satisfies Record<
  AppLanguage,
  TranslationShape<typeof AR_TRANSLATIONS>
>;