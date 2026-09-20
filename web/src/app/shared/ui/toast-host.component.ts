import { Component, inject } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast-host',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <div class="pointer-events-none fixed bottom-4 right-4 z-[80] flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="pointer-events-auto rounded-2xl px-4 py-3 text-sm shadow-lg"
             [class.bg-slate-900]="!toast.error"
             [class.text-white]="!toast.error"
             [class.bg-rose-600]="toast.error"
             [class.text-white]="toast.error"
             role="status">
          {{ toast.message | translate }}
        </div>
      }
    </div>
  `
})
export class ToastHostComponent {
  toastService = inject(ToastService);
  private i18n = inject(TranslateService);
}
