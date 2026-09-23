import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';
import { ThemeService } from '../../core/services/theme.service';

const PLATFORM_LOGO = '/assets/branding/logo.svg';

@Component({
  selector: 'app-brand-mark',
  standalone: true,
  imports: [TranslatePipe],
  host: { class: 'block min-w-0' },
  template: `
    <div class="flex w-full min-w-0 items-center gap-3" [class.justify-center]="centered()">
      <span class="grid h-9 w-9 shrink-0 overflow-hidden rounded-xl">
        @if (logoSrc(); as src) {
          <img [src]="src" [alt]="alt()" class="h-9 w-9 max-h-9 max-w-9 object-contain" (error)="failed.set(src)" />
        } @else {
          <span class="h-9 w-9 rounded-xl bg-brand-700" aria-hidden="true"></span>
        }
      </span>
      @if (showName()) {
        <span class="min-w-0 flex-1 truncate font-display text-base font-semibold">{{ label() || ('app.name' | translate) }}</span>
      }
    </div>
  `
})
export class BrandMarkComponent {
  private auth = inject(AuthService);
  private branding = inject(BrandingService);
  private theme = inject(ThemeService);
  showName = input(true);
  centered = input(false);
  preferDark = input(false);

  private preferred = computed(() => {
    if (this.auth.isSuperAdmin()) {
      return PLATFORM_LOGO;
    }
    const dark = this.preferDark() || this.theme.dark();
    return this.branding.logoUrl(dark);
  });

  failed = linkedSignal(() => {
    this.preferred();
    return null as string | null;
  });

  logoSrc = computed(() => {
    const preferred = this.preferred();
    const failed = this.failed();
    if (preferred && failed !== preferred) {
      return preferred;
    }
    if (preferred !== PLATFORM_LOGO && failed !== PLATFORM_LOGO) {
      return PLATFORM_LOGO;
    }
    return null;
  });

  label = computed(() => {
    if (this.auth.isSuperAdmin()) {
      return null;
    }
    const brand = this.branding.branding();
    return brand?.commercialName?.trim() || brand?.name?.trim() || null;
  });

  alt(): string {
    return this.label() || this.branding.displayName();
  }
}
