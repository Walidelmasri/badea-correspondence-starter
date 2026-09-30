import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';

import { TranslationService } from '../../../../core/i18n/translation.service';

export interface PdfDocumentProperties {
  readonly filename: string;
  readonly sizeBytes: number | null;
  readonly pageCount: number;

  readonly title?: string | null;
  readonly author?: string | null;
  readonly createdAt?: Date | null;
  readonly modifiedAt?: Date | null;
}

@Component({
  selector: 'app-pdf-properties-dialog',
  standalone: true,
  templateUrl: './pdf-properties-dialog.component.html',
  styleUrl: './pdf-properties-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfPropertiesDialogComponent implements AfterViewInit {
  private readonly dialog =
    viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly translation = inject(TranslationService);

  readonly properties = input.required<PdfDocumentProperties>();

  readonly closed = output<void>();

  private readonly locale = computed(() =>
    this.translation.language() === 'ar'
      ? 'ar-SA-u-nu-arab'
      : 'en-GB'
  );

  readonly hasExtendedMetadata = computed(() => {
    const properties = this.properties();

    return Boolean(
      properties.title ||
      properties.author ||
      properties.createdAt ||
      properties.modifiedAt
    );
  });

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat(this.locale(), {
      useGrouping: false,
    }).format(value);
  }

  formatFileSize(bytes: number | null): string {
    if (bytes === null || bytes < 0) {
      return this.translation.text().pdf.properties.unknown;
    }

    const units = ['B', 'KB', 'MB', 'GB'];

    let size = bytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    const formatted = new Intl.NumberFormat(this.locale(), {
      maximumFractionDigits: unitIndex === 0 ? 0 : 1,
    }).format(size);

    return `${formatted} ${units[unitIndex]}`;
  }

  formatDate(date: Date | null | undefined): string {
    if (!date || !Number.isFinite(date.getTime())) {
      return this.translation.text().pdf.properties.unknown;
    }

    const locale =
      this.translation.language() === 'ar'
        ? 'ar-SA-u-ca-gregory-nu-arab'
        : 'en-GB';

    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
    }).format(date);
  }
}