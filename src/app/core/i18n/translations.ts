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
    dashboard: 'لوحة المتابعة',
    inbox: 'الوارد',
    sent: 'المرسل',
    drafts: 'المسودات',
    completed: 'المكتمل',
    presidentReview: 'مراجعة الرئيس',

  },

  dashboard: {
    eyebrow: 'نظرة عامة • مراسلات الرئاسة',
    title: 'لوحة المتابعة',
    subtitle: 'صورة واضحة عن المراسلات التي يمكنك الوصول إليها والمهام التي تتطلب المتابعة.',
    scoped: 'الإحصاءات حسب صلاحيات حسابك الحالي',
    refresh: 'تحديث البيانات',
    refreshing: 'جارٍ التحديث...',
    loadFailed: 'تعذر تحميل مؤشرات المراسلات. لم تُعرض أرقام تقديرية.',
    retry: 'إعادة المحاولة',
    stats: {
      received: 'إجمالي الوارد',
      needsAction: 'يتطلب إجراء',
      handled: 'تم التعامل معه',
      archived: 'مؤرشف',
      sent: 'المراسلات المرسلة',
      drafts: 'المسودات النشطة',
    },
    focusTitle: 'ما يحتاج انتباهك',
    focusHint: 'المراسلات النشطة التي تتطلب إجراء منك أو من إدارتك',
    workloadTitle: 'توزيع الوارد',
    workloadHint: 'حسب حالة المتابعة في المراسلات المتاحة لك',
    recentTitle: 'أحدث المراسلات الواردة',
    recentHint: 'آخر المراسلات الواردة بحسب وقت التعديل',
    empty: 'لا توجد مراسلات واردة للعرض حالياً.',
    noNumber: 'دون رقم مستند',
    actionStatus: 'يحتاج إجراء',
    handledStatus: 'تم التعامل معه',
    archivedStatus: 'مؤرشف',
    openInbox: 'فتح الوارد',
    openSent: 'عرض المرسل',
    openDrafts: 'عرض المسودات',
    countsNote: 'المؤشرات من سجلات ألفريسكو الفعلية؛ قد تتغير أثناء التحديث.',
    role: { PRESIDENT: 'الرئيس', PRESIDENT_OFFICE: 'مكتب الرئيس', DIRECTOR: 'مدير الإدارة' },
  },

  mailbox: {
    inboxFilterLabel: 'تصفية الوارد',
    inboxFilters: { all: 'الكل', needs_action: 'يتطلب إجراء', handled: 'تم التعامل معه', archived: 'مؤرشف' },
    forwardToPresident: 'إرسال إلى الرئيس',
    confirmForwardToPresident: 'هل تريد إرسال هذه المراسلة إلى الرئيس؟',
    forwarding: 'جارٍ الإرسال...',
    forwardSucceeded: 'تم إرسال المراسلة إلى الرئيس.',
    forwardFailed: 'تعذر إرسال المراسلة. يرجى تحديث الوارد والتحقق من الحالة.',
    forwardUncertain: 'تعذر التأكد من نتيجة الإرسال. يرجى تحديث الصفحة والتحقق من الحالة قبل أي إعادة محاولة.',
    historyTitle: 'سجل المراسلة',
    historyLoading: 'جارٍ تحميل السجل...',
    historyFailed: 'تعذر تحميل سجل المراسلة. لا يمكن متابعة التوجيه قبل التحقق منه.',
    historyActions: {
      sentToOffice: 'أُرسلت إلى مكتب الرئيس',
      sentToPresident: 'أُرسلت إلى الرئيس',
      sentToDirector: 'أُرسلت إلى المدير',
      distributedToDepartments: 'توزيع إلى الإدارات', assignmentCompleted: 'اكتمل إجراء الإدارة',
      completed: 'اكتمل الإجراء',
      archived: 'أُغلقت المراسلة إدارياً',
      continued: 'بدأت دورة متابعة جديدة',
      directorContinued: 'طلبت الإدارة دورة متابعة جديدة',
    },

    openDocument: 'عرض PDF',
    previewTitle: 'معاينة المستند',
    previewLoading: 'جارٍ تحميل المستند...',
    previewFailed: 'تعذر فتح المستند. يرجى التحقق من الصلاحيات أو المحاولة مرة أخرى.',
    previewClose: 'إغلاق',

    loading: 'جارٍ تحميل المراسلات...',
    loadFailed: 'تعذر تحميل المراسلات.',
    retry: 'إعادة المحاولة',
    showing: 'عرض', of: 'من', previous: 'السابق', next: 'التالي',
    sender: 'المرسل', documentNumber: 'الرقم المرجعي للمراسلة',
    noDocumentNumber: 'لا يوجد رقم مستند', updated: 'آخر تعديل',
    description: 'وصف المراسلة', noDescription: 'لا يوجد وصف.',
    attachments: 'المستندات المرفقة', noAttachments: 'لا توجد مرفقات.',
    presidentDocuments: 'المستندات الرسمية المعتمدة',
    documentKinds: { ANNOTATED_PDF: 'مستند الرئيس المعلق عليه', HANDWRITTEN_EXPLANATION: 'الشرح بخط اليد', OFFICIAL_ACTION_SHEET: 'ورقة الإجراء الرسمية', CORRESPONDENCE_PACKET: 'ملف المراسلة الكامل' },
    departmentAssignments: 'توزيع الإدارات',
    departmentCode: 'رمز الإدارة', assignmentCopyStatus: 'للعلم فقط', assignmentCompletedAt: 'تاريخ الإنجاز', assignmentTo: 'للإجراء', assignmentCopy: 'نسخة للعلم',
    assignmentPending: 'قيد الإجراء', assignmentCompleted: 'تم الإنجاز',
    assignmentResponse: 'رد الإدارة', assignmentCompleteAction: 'إكمال إجراء الإدارة',
    assignmentSending: 'جارٍ حفظ الرد...', confirmAssignmentComplete: 'تأكيد إكمال إجراء إدارتك؟',
    assignmentFailed: 'تعذر حفظ الرد.', assignmentUncertain: 'تعذر تأكيد حفظ الرد. أعد التحميل للتحقق قبل إعادة المحاولة.',
    officeDecisionTitle: 'قرار مكتب الرئيس بعد اكتمال الإجراءات',
    officeDecisionHint: 'اختر متابعة المراسلة لدورة جديدة أو إغلاقها نهائياً. تبقى جميع الإجراءات والمستندات السابقة محفوظة.',
    continueTitle: 'متابعة المراسلة',
    continueHint: 'بدء دورة جديدة بنفس رقم المراسلة. يمكن لمكتب الرئيس إرسالها إلى الرئيس مرة أخرى.',
    continueCorrespondence: 'بدء دورة جديدة',
    continuing: 'جارٍ بدء الدورة...',
    confirmContinueCorrespondence: 'هل تريد بدء دورة جديدة لنفس المراسلة؟ ستبقى جميع السجلات السابقة محفوظة.',
    continueSucceeded: 'بدأت دورة جديدة. يمكنك إرسال المراسلة إلى الرئيس.',
    continueFailed: 'تعذر بدء دورة جديدة. يرجى التحقق من حالة الإدارات وإعادة التحميل.',
    continueUncertain: 'تعذر التأكد من بدء الدورة. أعد التحميل وتحقق من سجل المراسلة قبل إعادة المحاولة.',
    directorFollowUpTitle: 'متابعة جديدة من الإدارة',
    directorFollowUpHint: 'بعد اكتمال جميع الإجراءات، يمكنك إضافة مستندات جديدة وإرسال المراسلة نفسها إلى مكتب الرئيس لبدء دورة أخرى.',
    directorFollowUpReason: 'سبب المتابعة (إلزامي)',
    directorFollowUpFiles: 'ملفات PDF إضافية (اختياري)',
    directorFollowUpFileHint: 'حتى 5 ملفات، حجم كل ملف لا يتجاوز 25 ميغابايت.',
    directorFollowUpSubmit: 'إرسال متابعة جديدة إلى مكتب الرئيس',
    directorFollowUpSending: 'جارٍ إرسال المتابعة...',
    directorFollowUpConfirm: 'هل تريد بدء دورة جديدة مع حفظ جميع السجلات السابقة؟',
    directorFollowUpFailed: 'تعذر إرسال المتابعة. أعد التحميل وتحقق من المرفقات.',
    directorFollowUpUncertain: 'نتيجة الإرسال غير مؤكدة. أعد التحميل وتحقق قبل المحاولة مجدداً لتجنب التكرار.',
    directorFollowUpInvalidFile: 'الملفات يجب أن تكون PDF فقط، بحد أقصى خمسة ملفات و25 ميغابايت لكل ملف.',
    administrativeClosureTitle: 'الإغلاق الإداري',
    administrativeClosureHint: 'اكتملت جميع الإجراءات المطلوبة. سيصبح هذا الملف للقراءة فقط بعد الإغلاق.',
    administrativeClosureReason: 'سبب الإغلاق (إلزامي)',
    administrativeClose: 'إغلاق المراسلة وأرشفتها',
    administrativeClosing: 'جارٍ الإغلاق...',
    confirmAdministrativeClosure: 'هل تريد إغلاق هذه المراسلة نهائياً؟ لا يمكن التراجع عن الأرشفة.',
    administrativeCloseFailed: 'تعذر إغلاق المراسلة. يرجى التحقق من حالة جميع الإدارات.',
    administrativeCloseUncertain: 'تعذر التأكد من نتيجة الإغلاق. أعد التحميل وتحقق قبل إعادة المحاولة.',

    loadingDetails: 'جارٍ تحميل تفاصيل المراسلة...',
    detailsFailed: 'تعذر تحميل تفاصيل هذه المراسلة.',
    openPresidentReview: 'فتح مراجعة الرئيس',
    states: {
      draft: 'مسودة', office: 'مكتب الرئيس',
      president: 'عند الرئيس', director: 'عند المدير', archived: 'مؤرشفة',
    },
  },

  officeCompletion: {
    title: 'استكمال مكتب الرئيس',
    description: 'إضافات المكتب تحفظ بشكل مستقل عن توجيهات الرئيس، ولا ترسل المراسلة.',
    presidentOriginal: 'توجيهات الرئيس (محفوظة دون تعديل)',
    officeAdditions: 'إضافات مكتب الرئيس',
    to: 'إلى', copy: 'نسخة إلى', actionsTitle: 'الإجراء المطلوب',
    presidentExplanation: 'توضيح الرئيس',
    presidentInkHint: 'تبقى الكتابة اليدوية وتعليقات PDF ضمن مراجعة الرئيس الأصلية.',
    otherRecipient: 'جهة أخرى (إضافة المكتب)',
    otherAction: 'إجراء آخر (إضافة المكتب)',
    additionalExplanation: 'توضيح إضافي من المكتب',
    officeNotes: 'ملاحظات إدارية',
    loading: 'جارٍ تحميل الاستكمال...', loadFailed: 'تعذر تحميل بيانات استكمال المكتب.',
    reload: 'إعادة التحميل', save: 'حفظ استكمال المكتب', saving: 'جارٍ الحفظ...',
    dispatch: 'إرسال إلى الإدارات', dispatching: 'جارٍ الإرسال...',
    confirmDispatch: 'إرسال التوجيهات المعتمدة إلى الإدارات المحددة؟',
    saveBeforeDispatch: 'احفظ تعديلات المكتب قبل الإرسال.',
    status: { sent: 'تم التوزيع إلى الإدارات مع ورقة الإجراء الرسمية.', saved: 'تم الحفظ مع سجل التعديل.', failed: 'تعذر الحفظ.',
      conflict: 'تم تعديل البيانات في مكان آخر. أعد التحميل قبل الحفظ.',
      uncertain: 'تعذر تأكيد الحفظ. تحقق من السجل قبل المحاولة مجدداً.' },
    actions: {
      'required-action': 'الإجراء اللازم', 'study-and-opinion': 'الدراسة وإبداء الرأي',
      'review-and-advise': 'المراجعة والإفادة', 'discuss-directly': 'المناقشة مباشرة',
      'direct-response': 'الرد المباشر', 'prepare-summary': 'إعداد ملخص',
      'information-and-action': 'للعلم والإجراء', 'follow-up': 'المتابعة',
      'information-and-file': 'للعلم والحفظ', 'act-per-phone-call': 'اتخاذ الإجراء وفق الاتصال',
      'prepare-response': 'إعداد رد', 'apology': 'الاعتذار',
      'return-later': 'يعرض لاحقاً', 'other': 'أخرى',
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
    documentNumber: 'الرقم المرجعي للمراسلة',
    noDocumentNumber: 'لا يوجد رقم مستند',
    createdAt: 'تاريخ الإنشاء',
    modifiedAt: 'آخر تعديل',
    editDraft: 'تعديل المسودة',
    resumeReview: 'متابعة مراجعة الرئيس',
    officeDraft: 'استكمال المكتب',
    previewDocument: 'عرض PDF',

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
      documentNumber: 'الرقم المرجعي للمراسلة',
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
      documentNumber: 'يُنشأ تلقائياً عند الإرسال',
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
    feedback: {
      subjectRequired: 'يرجى إدخال موضوع المراسلة أولاً.',
      loading: 'جارٍ فتح المسودة...',
      loadFailed: 'تعذر فتح المسودة. تحقق من صلاحياتك أو أعد تحميل الصفحة.',
      notEditable: 'هذه المراسلة ليست مسودة تنفيذية قابلة للتعديل.',
      saved: 'تم حفظ المسودة والمرفقات المؤكدة.',
      saveFailed: 'تعذر حفظ المسودة.',
      saveUncertain: 'تعذر تأكيد الحفظ. أعد تحميل المسودات للتحقق قبل أي إعادة محاولة.',
      pdfOnly: 'ملفات PDF فقط، بحد أقصى 25 ميغابايت لكل ملف',
      invalidPdf: 'اختر ملفات PDF صالحة لا تتجاوز 25 ميغابايت لكل ملف.',
      savedFile: 'محفوظ في ألفريسكو',
      removeFile: 'إزالة',
      retryUpload: 'إعادة محاولة الرفع',
      uploadFailed: 'تم حفظ المسودة لكن فشل رفع بعض المرفقات. يمكنك إعادة محاولة الملفات الفاشلة.',
      uploadUncertain: 'نتيجة رفع بعض الملفات غير مؤكدة. تحقق من المستندات قبل المحاولة مجدداً.',
      confirmDelete: 'هل تريد حذف المرفق؟',
      deleteFailed: 'تعذر تأكيد حذف المرفق. أعد التحميل للتحقق قبل أي تعديل آخر.',
      confirmCancel: 'هناك تغييرات غير محفوظة. هل تريد الخروج؟',
      sendPending: 'الإرسال غير مفعّل بعد حتى اكتمال اختبار التوجيه الآمن في الخطوة التالية.',
      sendIncomplete: 'أكمل الحقول الإلزامية قبل إرسال المراسلة.',
      sendBlocked: 'لم تُرسل المراسلة: تحقق من حفظ المسودة ونجاح رفع جميع المرفقات.',
      confirmSend: 'هل تريد إرسال هذه المراسلة إلى مكتب الرئيس؟',
      sent: 'تم إرسال المراسلة إلى مكتب الرئيس.',
      sendFailed: 'تعذر إرسال المراسلة. راجع صلاحياتك أو حالة المسودة قبل المحاولة.',
      sendUncertain: 'تعذر تأكيد حالة الإرسال. راجع المسودات والمرسل قبل إعادة المحاولة.',
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
  documentNumber: 'الرقم المرجعي للمراسلة',
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
    confirmReturn: 'إعادة المراسلة إلى مكتب الرئيس؟',
    loadDocumentsFailed: 'تعذر تحميل المراسلة أو المستندات الأصلية. يرجى إعادة المحاولة.',
    retryLoadDocuments: 'إعادة التحميل',
    loadingDocuments: 'جارٍ تحميل المراسلة والمستندات الأصلية...',
    handoffNotConfirmed: 'تعذر تأكيد تسليم المراسلة. تحقق من حالة المراسلة قبل المحاولة مجدداً.',
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
    dashboard: 'Dashboard',
    inbox: 'Inbox',
    sent: 'Sent',
    drafts: 'Drafts',
    completed: 'Completed',
    presidentReview: 'President Review',
  },

  dashboard: {
    eyebrow: 'OVERVIEW • PRESIDENCY CORRESPONDENCE',
    title: 'Correspondence dashboard',
    subtitle: 'A clear view of your accessible correspondence and the work requiring attention.',
    scoped: 'Statistics are scoped to your current access',
    refresh: 'Refresh data',
    refreshing: 'Refreshing...',
    loadFailed: 'Correspondence metrics could not be loaded. No estimated figures are shown.',
    retry: 'Retry',
    stats: {
      received: 'Received',
      needsAction: 'Needs action',
      handled: 'Previously handled',
      archived: 'Archived',
      sent: 'Sent correspondence',
      drafts: 'Active drafts',
    },
    focusTitle: 'Requires your attention',
    focusHint: 'Active correspondence awaiting action by you or your department',
    workloadTitle: 'Inbox breakdown',
    workloadHint: 'Current status of correspondence available to your role',
    recentTitle: 'Recent correspondence',
    recentHint: 'Recently updated incoming correspondence',
    empty: 'No received correspondence to display yet.',
    noNumber: 'No document number',
    actionStatus: 'Needs action',
    handledStatus: 'Previously handled',
    archivedStatus: 'Archived',
    openInbox: 'Open Inbox',
    openSent: 'View Sent',
    openDrafts: 'View Drafts',
    countsNote: 'Metrics use real Alfresco records; values may change during refresh.',
    role: { PRESIDENT: 'President', PRESIDENT_OFFICE: 'President’s Office', DIRECTOR: 'Department Director' },
  },

  mailbox: {
    inboxFilterLabel: 'Inbox filter',
    inboxFilters: { all: 'All', needs_action: 'Needs action', handled: 'Previously handled', archived: 'Archived' },
    forwardToPresident: 'Forward to President',
    confirmForwardToPresident: 'Forward this correspondence to the President?',
    forwarding: 'Forwarding...',
    forwardSucceeded: 'Correspondence forwarded to the President.',
    forwardFailed: 'Unable to forward correspondence. Refresh Inbox and verify its status.',
    forwardUncertain: 'The forwarding result could not be confirmed. Refresh and verify before trying again.',
    historyTitle: 'Correspondence history',
    historyLoading: 'Loading history...',
    historyFailed: 'History could not be loaded. Forwarding is disabled until it can be verified.',
    historyActions: {
      sentToOffice: 'Sent to President’s Office',
      sentToPresident: 'Forwarded to President',
      sentToDirector: 'Sent to Director',
      distributedToDepartments: 'Distributed to departments', assignmentCompleted: 'Department assignment completed',
      completed: 'Marked complete',
      archived: 'Administratively archived',
      continued: 'Another workflow round started',
      directorContinued: 'Director started another correspondence round',
    },

    openDocument: 'View PDF',
    previewTitle: 'Document Preview',
    previewLoading: 'Loading PDF...',
    previewFailed: 'Unable to open this document. Check permissions or try again.',
    previewClose: 'Close',

    loading: 'Loading correspondence...',
    loadFailed: 'Could not load correspondence.',
    retry: 'Retry',
    showing: 'Showing', of: 'of', previous: 'Previous', next: 'Next',
    sender: 'Sender', documentNumber: 'Correspondence Reference',
    noDocumentNumber: 'No document number', updated: 'Last updated',
    description: 'Description', noDescription: 'No description provided.',
    attachments: 'Attached Documents', noAttachments: 'No attachments.',
    presidentDocuments: 'Finalized official documents',
    documentKinds: { ANNOTATED_PDF: 'President-annotated PDF', HANDWRITTEN_EXPLANATION: 'Handwritten explanation', OFFICIAL_ACTION_SHEET: 'Official action sheet', CORRESPONDENCE_PACKET: 'Complete correspondence PDF' },
    departmentAssignments: 'Department assignments',
    departmentCode: 'Department code', assignmentCopyStatus: 'For information only', assignmentCompletedAt: 'Completed on', assignmentTo: 'Action required', assignmentCopy: 'Information copy',
    assignmentPending: 'Pending', assignmentCompleted: 'Completed',
    assignmentResponse: 'Department response', assignmentCompleteAction: 'Complete my department assignment',
    assignmentSending: 'Saving response...', confirmAssignmentComplete: 'Confirm your department assignment is complete?',
    assignmentFailed: 'Could not save the response.', assignmentUncertain: 'Response could not be confirmed. Reload and verify before retrying.',
    officeDecisionTitle: 'President Office decision',
    officeDecisionHint: 'Start another workflow round or close permanently. Previous actions and documents remain preserved.',
    continueTitle: 'Continue correspondence',
    continueHint: 'Start a new round under the same correspondence ID. The Office can forward it to the President again.',
    continueCorrespondence: 'Start another round',
    continuing: 'Starting next round...',
    confirmContinueCorrespondence: 'Start another round for this correspondence? Prior actions and documents will be retained.',
    continueSucceeded: 'Another round started. You can forward the correspondence to the President.',
    continueFailed: 'Could not continue correspondence. Check department completion and reload.',
    continueUncertain: 'Could not confirm whether a new round started. Reload and check history before retrying.',
    directorFollowUpTitle: 'Director follow-up',
    directorFollowUpHint: 'After all departments complete their work, add supplementary PDFs and return this same correspondence to the Office for another round.',
    directorFollowUpReason: 'Reason for follow-up (required)',
    directorFollowUpFiles: 'Supplementary PDF attachments (optional)',
    directorFollowUpFileHint: 'Up to 5 PDFs, 25 MiB maximum per file.',
    directorFollowUpSubmit: 'Start follow-up with President Office',
    directorFollowUpSending: 'Starting follow-up...',
    directorFollowUpConfirm: 'Start another round under the same correspondence and preserve all previous records?',
    directorFollowUpFailed: 'Follow-up could not be completed. Reload and check attachments.',
    directorFollowUpUncertain: 'The result is uncertain. Reload and check before retrying to avoid duplicates.',
    directorFollowUpInvalidFile: 'Select only PDF files (up to five files, 25 MiB each).',
    administrativeClosureTitle: 'Administrative closure',
    administrativeClosureHint: 'All required departments have completed their actions. Closure makes this correspondence read-only.',
    administrativeClosureReason: 'Closure reason (required)',
    administrativeClose: 'Close and archive correspondence',
    administrativeClosing: 'Closing...',
    confirmAdministrativeClosure: 'Permanently close this correspondence? Archiving cannot be undone.',
    administrativeCloseFailed: 'Could not close this correspondence. Verify all department assignments.',
    administrativeCloseUncertain: 'Closure could not be confirmed. Reload and verify before retrying.',

    loadingDetails: 'Loading correspondence details...',
    detailsFailed: 'Could not load correspondence details.',
    openPresidentReview: 'Open President Review',
    states: {
      draft: 'Draft', office: 'With President’s Office',
      president: 'With President', director: 'With Director', archived: 'Archived',
    },
  },

  officeCompletion: {
    title: 'President Office Completion',
    description: 'Office additions are saved separately from President directions. Saving does not send correspondence.',
    presidentOriginal: 'President directions (preserved)',
    officeAdditions: 'Office additions',
    to: 'To', copy: 'Copy to', actionsTitle: 'Required actions',
    presidentExplanation: 'President explanation',
    presidentInkHint: 'Handwritten directions and PDF annotations remain in the original President review.',
    otherRecipient: 'Other recipient (Office addition)',
    otherAction: 'Other action (Office addition)',
    additionalExplanation: 'Additional Office explanation',
    officeNotes: 'Administrative notes',
    loading: 'Loading Office completion...', loadFailed: 'Unable to load Office completion.',
    reload: 'Reload', save: 'Save Office Completion', saving: 'Saving...',
    dispatch: 'Send to departments', dispatching: 'Distributing...',
    confirmDispatch: 'Distribute this correspondence and official action sheet to the selected departments?',
    saveBeforeDispatch: 'Save Office changes before distributing.',
    status: { sent: 'Official action sheet and assignments dispatched.', saved: 'Saved with revision history.', failed: 'Save failed.',
      conflict: 'Someone changed this record. Reload before saving.',
      uncertain: 'Save could not be confirmed. Verify the record before retrying.' },
    actions: {
      'required-action': 'Required action', 'study-and-opinion': 'Study and opinion',
      'review-and-advise': 'Review and advise', 'discuss-directly': 'Discuss directly',
      'direct-response': 'Direct response', 'prepare-summary': 'Prepare summary',
      'information-and-action': 'For information and action', 'follow-up': 'Follow up',
      'information-and-file': 'For information and filing', 'act-per-phone-call': 'Action per phone call',
      'prepare-response': 'Prepare response', 'apology': 'Apologize',
      'return-later': 'Present later', 'other': 'Other',
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
    documentNumber: 'Correspondence Reference',
    noDocumentNumber: 'No document number',
    createdAt: 'Created',
    modifiedAt: 'Last Modified',
    editDraft: 'Edit Draft',
    resumeReview: 'Continue President Review',
    officeDraft: 'Continue Office Completion',
    previewDocument: 'View PDF',

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
      documentNumber: 'Correspondence Reference',
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
      documentNumber: 'Assigned automatically when sent',
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
    feedback: {
      subjectRequired: 'Enter a subject before saving a draft.',
      loading: 'Opening draft...',
      loadFailed: 'Unable to open this draft. Check your access or refresh the page.',
      notEditable: 'This is not an editable executive draft.',
      saved: 'Draft and confirmed attachments saved.',
      saveFailed: 'Could not save the draft.',
      saveUncertain: 'Save outcome is uncertain. Refresh Drafts to verify before trying again.',
      pdfOnly: 'PDF files only, maximum 25 MiB per file',
      invalidPdf: 'Select valid PDFs, no more than 25 MiB each.',
      savedFile: 'Saved in Alfresco',
      removeFile: 'Remove',
      retryUpload: 'Retry upload',
      uploadFailed: 'Draft saved, but some attachments failed. You can retry confirmed failures.',
      uploadUncertain: 'Some upload outcomes are uncertain. Check stored documents before retrying.',
      confirmDelete: 'Delete this attachment?',
      deleteFailed: 'Could not confirm deletion. Refresh to verify before making further changes.',
      confirmCancel: 'You have unsaved changes. Leave this page?',
      sendPending: 'Sending stays disabled until the safe executive routing checks are completed in the next batch.',
      sendIncomplete: 'Complete all required fields before sending correspondence.',
      sendBlocked: 'Not sent. Confirm the draft was saved and every attachment uploaded successfully.',
      confirmSend: 'Send this correspondence to the President’s Office?',
      sent: 'Correspondence sent to the President’s Office.',
      sendFailed: 'Could not send. Check your permissions and the draft state before trying again.',
      sendUncertain: 'Could not confirm the send. Check Drafts and Sent before trying again.',
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
  documentNumber: 'Correspondence Reference',
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
    confirmReturn: 'Return this correspondence to the President’s Office?',
    loadDocumentsFailed: 'Unable to load the correspondence or original PDFs. Please retry.',
    retryLoadDocuments: 'Retry load',
    loadingDocuments: 'Loading correspondence and original PDFs...',
    handoffNotConfirmed: 'Handoff could not be confirmed. Verify correspondence state before retrying.',
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