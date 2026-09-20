import { Component, computed, inject, input, signal } from '@angular/core';
import { BrandingService } from '../../core/services/branding.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-brand-mark',
  standalone: true,
  template: `
    <div class="flex min-w-0 items-center gap-3" [class.justify-center]="centered()">
      @if (logo()) {
        <img [src]="logo()!" [alt]="branding.displayName()" class="h-9 w-9 rounded-xl object-cover" (error)="failed.set(true)" />
      } @else {
        <span class="grid h-9 w-9 shrink-0 place-items-center overflow-hidden rounded-xl bg-brand-700" aria-hidden="true">
          <img src="/assets/branding/logo.png" alt="" class="h-9 w-9 object-cover" />
        </span>
      }
      @if (showName()) {
        <span class="truncate font-display text-base font-semibold">{{ branding.displayName() }}</span>
      }
    </div>
  `
})
export class BrandMarkComponent {
  branding = inject(BrandingService);
  private theme = inject(ThemeService);
  showName = input(true);
  centered = input(false);
  failed = signal(false);

  logo = computed(() => {
    if (this.failed()) {
      return null;
    }
    const dark = this.theme.mode() === 'dark' || document.documentElement.classList.contains('dark');
    return this.branding.logoUrl(dark);
  });
}
