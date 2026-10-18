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
    presidentReview: 'مراجعة الرئيس',

  },

  mailbox: {
    loading: 'جارٍ تحميل المراسلات...',
    loadFailed: 'تعذر تحميل المراسلات.',
    retry: 'إعادة المحاولة',
    showing: 'عرض', of: 'من', previous: 'السابق', next: 'التالي',
    sender: 'المرسل', documentNumber: 'رقم المستند',
    noDocumentNumber: 'لا يوجد رقم مستند', updated: 'آخر تعديل',
    description: 'وصف المراسلة', noDescription: 'لا يوجد وصف.',
    attachments: 'المستندات المرفقة', noAttachments: 'لا توجد مرفقات.',
    loadingDetails: 'جارٍ تحميل تفاصيل المراسلة...',
    detailsFailed: 'تعذر تحميل تفاصيل هذه المراسلة.',
    openPresidentReview: 'فتح مراجعة الرئيس',
    states: {
      draft: 'مسودة', office: 'مكتب الرئيس',
      president: 'عند الرئيس', director: 'عند المدير', archived: 'مؤرشفة',
    },
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

  loading: 'جارٍ تحميل المسودات...',
  loadFailed: 'تعذر تحميل المسودات. يرجى المحاولة مرة أخرى.',
  retry: 'إعادة المحاولة',

  pagination: {
    showing: 'عرض',
    of: 'من',
    previous: 'السابق',
    next: 'التالي',
  },

  detail: {
    documentNumber: 'رقم المستند',
    noDocumentNumber: 'لا يوجد رقم مستند',
    createdAt: 'تاريخ الإنشاء',
    modifiedAt: 'آخر تعديل',
    editDraft: 'تعديل المسودة',

    description: 'وصف المراسلة',
    noDescription: 'لم تتم إضافة وصف للمراسلة.',

    attachments: 'المستندات المرفقة',
    noAttachments: 'لا توجد مستندات مرفقة.',

    loading: 'جارٍ تحميل تفاصيل المراسلة...',
    loadFailed: 'تعذر تحميل تفاصيل المسودة.',
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
      searchPlaceholder: 'ابحث داخل المستند',
      previousMatch: 'النتيجة السابقة',
      nextMatch: 'النتيجة التالية',
      closeSearch: 'إغلاق البحث', signature: 'توقيع',
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
  auth: {
    eyebrow: 'مراسلات الرئاسة',

    title: 'تسجيل الدخول',

    description:
      'استخدم حساب BADEA للدخول إلى نظام مراسلات الرئاسة.',

    username: 'اسم المستخدم',

    usernamePlaceholder:
      'مثال: firstname.lastname',

    password: 'كلمة المرور',

    passwordPlaceholder:
      'أدخل كلمة المرور',

    signIn: 'تسجيل الدخول',

    signingIn:
      'جارٍ تسجيل الدخول...',

    signOut: 'تسجيل الخروج',

    invalidCredentials:
      'اسم المستخدم أو كلمة المرور غير صحيحة.',

    unavailable:
      'تعذر الاتصال بخدمة تسجيل الدخول. حاول مرة أخرى.',
  },
  user: {
    office: 'مكتب الرئيس',
  },
  director: {
  eyebrow: 'مساحة عمل المدير',
  title: 'مراسلة جديدة',
  editing: 'تعديل مسودة مراسلة',

  description:
    'إعداد المراسلة والمستندات الداعمة لإرسالها إلى مكتب الرئيس.',

  details: 'بيانات المراسلة',
  subject: 'الموضوع',
  documentNumber: 'رقم المستند',
  documentDate: 'تاريخ المستند',
  source: 'المصدر',
  body: 'وصف المراسلة',

  attachments: 'المستندات المرفقة',
  chooseFiles: 'اختيار ملفات PDF',
  fileHint: 'PDF فقط · الحد الأقصى 25 ميبيبايت لكل ملف',

  save: 'حفظ المسودة ورفع الملفات',
  saving: 'جارٍ الحفظ…',
  loading: 'جارٍ تحميل المراسلة…',
  saved: 'تم حفظ المسودة بنجاح في نظام ألفريسكو.',

  subjectRequired: 'يرجى إدخال موضوع المراسلة.',
  invalidFile: 'يُسمح بملفات PDF فقط وبحد أقصى 25 ميبيبايت.',

  createFailed: 'تعذر إنشاء المسودة. يرجى المحاولة مرة أخرى.',
  createUncertain:
    'انقطع الاتصال قبل تأكيد النتيجة. ربما تم إنشاء المسودة. يرجى التحقق قبل إعادة المحاولة.',

  loadFailed: 'تعذر تحميل المراسلة. يرجى المحاولة مرة أخرى.',
  notEditable:
    'لم تعد هذه المراسلة مسودة ولا يمكن تعديلها من هذه الصفحة.',

  updateFailed: 'تعذر تحديث المسودة. يرجى المحاولة مرة أخرى.',
  updateUncertain:
    'تعذر تأكيد نتيجة الحفظ. يرجى تحديث الصفحة والتحقق من آخر البيانات المحفوظة.',

  uploadFailed:
    'تعذر رفع بعض المستندات. ستبقى المراسلة في حالة مسودة.',
  uploadUncertain:
    'تعذر تأكيد نتيجة رفع أحد المستندات. يرجى التحقق قبل إعادة المحاولة.',

  savedReference: 'المعرف الفني للمراسلة',
  savedDocument: 'محفوظ في ألفريسكو',

  locked: 'يمكنك متابعة تعديل هذه المسودة حتى يتم إرسالها.',

  pending: 'بانتظار الرفع',
  uploading: 'جارٍ الرفع',
  failed: 'فشل الرفع',
  verify: 'بحاجة إلى التحقق',

  retry: 'إعادة المحاولة',
  remove: 'إزالة',

  confirmDelete:
    'هل تريد إزالة هذا المستند من المراسلة؟',
  deleteSucceeded: 'تمت إزالة المستند من المسودة.',
  deleteFailed:
    'تعذر إزالة المستند. يرجى التحقق من الصلاحيات والمحاولة مجدداً.',
  deleteUncertain:
    'تعذر التأكد من نتيجة الحذف. يرجى تحديث الصفحة والتحقق من المرفقات قبل المتابعة.',

  automaticDate:
    'يتم تسجيل تاريخ ووقت إنشاء المراسلة تلقائياً في ألفريسكو.',

  destination: 'الوجهة: مكتب الرئيس',

    send: 'إرسال إلى مكتب الرئيس',
    sending: 'جارٍ الإرسال…',
    confirmSend: 'هل تريد إرسال المراسلة إلى مكتب الرئيس؟ لن تتمكن من تعديلها كمسودة بعد الإرسال.',
    sent: 'تم إرسال المراسلة إلى مكتب الرئيس بنجاح.',
    sendBlocked: 'لم يتم إرسال المراسلة. يوجد مستند لم يتم رفعه بنجاح أو يحتاج إلى التحقق. راجع المرفقات أولاً.',
    sendFailed: 'تعذر إرسال المراسلة. يرجى إعادة تحميل المسودات والتحقق من حالتها قبل المحاولة مجدداً.',
    sendUncertain: 'تعذر تأكيد نتيجة الإرسال. لا تحاول الإرسال مرة ثانية قبل التحقق من المراسلة في المسودات أو المرسل.',
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
    eraser: 'ممحاة',
    undo: 'تراجع',
    redo: 'إعادة',
    page: 'صفحة',
    previousPage: 'الصفحة السابقة',
    nextPage: 'الصفحة التالية',
    addPage: 'صفحة إضافية',
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
    presidentReview: 'President Review',
  },

  mailbox: {
    loading: 'Loading correspondence...',
    loadFailed: 'Could not load correspondence.',
    retry: 'Retry',
    showing: 'Showing', of: 'of', previous: 'Previous', next: 'Next',
    sender: 'Sender', documentNumber: 'Document Number',
    noDocumentNumber: 'No document number', updated: 'Last updated',
    description: 'Description', noDescription: 'No description provided.',
    attachments: 'Attached Documents', noAttachments: 'No attachments.',
    loadingDetails: 'Loading correspondence details...',
    detailsFailed: 'Could not load correspondence details.',
    openPresidentReview: 'Open President Review',
    states: {
      draft: 'Draft', office: 'With President’s Office',
      president: 'With President', director: 'With Director', archived: 'Archived',
    },
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

  loading: 'Loading drafts...',
  loadFailed: 'Unable to load drafts. Please try again.',
  retry: 'Retry',

  pagination: {
    showing: 'Showing',
    of: 'of',
    previous: 'Previous',
    next: 'Next',
  },

  detail: {
    documentNumber: 'Document Number',
    noDocumentNumber: 'No document number',
    createdAt: 'Created',
    modifiedAt: 'Last Modified',
    editDraft: 'Edit Draft',

    description: 'Description',
    noDescription: 'No description provided.',

    attachments: 'Attached Documents',
    noAttachments: 'No documents attached.',

    loading: 'Loading correspondence details...',
    loadFailed: 'Unable to load the selected draft.',
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
      searchPlaceholder: 'Search this document',
      previousMatch: 'Previous result',
      nextMatch: 'Next result',
      closeSearch: 'Close search', signature: 'Signature',
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
  director: {
  eyebrow: 'DIRECTOR WORKSPACE',
  title: 'New Correspondence',
  editing: 'Edit Correspondence Draft',

  description:
    'Prepare correspondence and supporting documents for the President’s Office.',

  details: 'Correspondence Details',
  subject: 'Subject',
  documentNumber: 'Document Number',
  documentDate: 'Document Date',
  source: 'Source',
  body: 'Description',

  attachments: 'Supporting Documents',
  chooseFiles: 'Choose PDF Documents',
  fileHint: 'PDF only · Maximum 25 MiB per document',

  save: 'Save Draft & Upload',
  saving: 'Saving…',
  loading: 'Loading correspondence…',
  saved: 'Draft saved successfully in Alfresco.',

  subjectRequired: 'Please enter a subject.',
  invalidFile: 'Only PDF files up to 25 MiB are supported.',

  createFailed:
    'The draft could not be created. Please try again.',
  createUncertain:
    'The server response was interrupted. The draft may have been created. Do not retry until its status is verified.',

  loadFailed:
    'Unable to load this correspondence. Please try again.',
  notEditable:
    'This correspondence is no longer a draft and cannot be edited here.',

  updateFailed:
    'The draft could not be updated. Please try again.',
  updateUncertain:
    'The save result could not be confirmed. Refresh the draft to verify its latest values.',

  uploadFailed:
    'Some documents could not be uploaded. The correspondence remains a draft.',
  uploadUncertain:
    'An upload result could not be confirmed. Verify the document before trying again.',

  savedReference: 'Correspondence ID',
  savedDocument: 'Saved in Alfresco',

  locked:
    'You can continue editing this draft until it is submitted.',

  pending: 'Queued',
  uploading: 'Uploading',
  failed: 'Failed',
  verify: 'Needs verification',

  retry: 'Retry',
  remove: 'Remove',

  confirmDelete:
    'Remove this document from the correspondence?',
  deleteSucceeded:
    'Document removed from the draft.',
  deleteFailed:
    'The document could not be removed. Check your permissions and try again.',
  deleteUncertain:
    'The deletion result could not be verified. Refresh the page and check the attachments before continuing.',

  automaticDate:
    'The creation timestamp is recorded automatically by Alfresco.',

  destination: 'Destination: President’s Office',

    send: 'Send to President’s Office',
    sending: 'Sending…',
    confirmSend: 'Send this correspondence to the President’s Office? You will no longer be able to edit it as a draft.',
    sent: 'Correspondence sent to the President’s Office.',
    sendBlocked: 'Not sent. At least one attachment failed to upload or needs verification. Resolve the attachments first.',
    sendFailed: 'The correspondence could not be sent. Reload Drafts and check its status before trying again.',
    sendUncertain: 'The send result could not be confirmed. Do not send again until you have checked Drafts or Sent.',
},
  auth: {
    eyebrow:
      'Presidency Correspondence',

    title: 'Sign in',

    description:
      'Use your BADEA account to access Presidency Correspondence.',

    username: 'Username',

    usernamePlaceholder:
      'e.g. firstname.lastname',

    password: 'Password',

    passwordPlaceholder:
      'Enter your password',

    signIn: 'Sign in',

    signingIn: 'Signing in...',

    signOut: 'Sign out',

    invalidCredentials:
      'The username or password is incorrect.',

    unavailable:
      'The sign-in service could not be reached. Try again.',
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
    eraser: 'Eraser',
    undo: 'Undo',
    redo: 'Redo',
    page: 'Page',
    previousPage: 'Previous page',
    nextPage: 'Next page',
    addPage: 'Add page',
  },
} as const satisfies TranslationShape<typeof AR_TRANSLATIONS>;

export const TRANSLATIONS = {
  ar: AR_TRANSLATIONS,
  en: EN_TRANSLATIONS,
} as const satisfies Record<
  AppLanguage,
  TranslationShape<typeof AR_TRANSLATIONS>
>;