import { Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ToastService } from '../../core/services/toast.service';

const MAX_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-image-upload',
  standalone: true,
  imports: [TranslatePipe],
  host: { class: 'block' },
  template: `
    <div>
      <p class="text-sm font-medium">{{ label() }}</p>
      <div class="mt-2 rounded-2xl border border-dashed border-slate-300 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-950/40"
           [class.border-brand-500]="dragging()"
           (dragover)="onDrag($event, true)" (dragleave)="onDrag($event, false)" (drop)="onDrop($event)">
        @if (src()) {
          <img [src]="src()!" [alt]="label()" class="mb-3 h-24 w-24 rounded-xl border border-slate-200 object-contain bg-white dark:border-slate-700" />
        }
        <p class="text-sm text-slate-600 dark:text-slate-300">{{ 'uploads.drop' | translate }}</p>
        <p class="my-1 text-xs text-slate-400">{{ 'uploads.or' | translate }}</p>
        <div class="flex flex-wrap gap-2">
          <button type="button" class="btn-secondary text-xs" [disabled]="disabled()" (click)="open()">
            {{ (src() ? 'uploads.change' : 'uploads.select') | translate }}
          </button>
          @if (src()) {
            <button type="button" class="btn-secondary text-xs" [disabled]="disabled()" (click)="cleared.emit()">
              {{ 'uploads.remove' | translate }}
            </button>
          }
        </div>
        <p class="mt-2 text-xs text-slate-400">{{ hint() || ('uploads.hint' | translate) }}</p>
      </div>
      <input #file class="sr-only" type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" (change)="onChange($event)" />
    </div>
  `
})
export class ImageUploadComponent {
  private toast = inject(ToastService);
  label = input('');
  hint = input('');
  src = input<string | null | undefined>(null);
  disabled = input(false);
  selected = output<File>();
  cleared = output<void>();
  dragging = signal(false);
  private file = viewChild<ElementRef<HTMLInputElement>>('file');

  open(): void {
    this.file()?.nativeElement.click();
  }

  onChange(event: Event): void {
    const chosen = (event.target as HTMLInputElement).files?.[0];
    if (chosen) {
      this.emit(chosen);
    }
    (event.target as HTMLInputElement).value = '';
  }

  onDrag(event: DragEvent, active: boolean): void {
    event.preventDefault();
    this.dragging.set(active);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const chosen = event.dataTransfer?.files?.[0];
    if (chosen) {
      this.emit(chosen);
    }
  }

  private emit(file: File): void {
    const name = file.name.toLowerCase();
    const allowed = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp'
      || /\.(jpe?g|png|webp)$/.test(name);
    if (!allowed || file.size > MAX_BYTES) {
      this.toast.show('uploads.invalid', true);
      return;
    }
    this.selected.emit(file);
  }
}
