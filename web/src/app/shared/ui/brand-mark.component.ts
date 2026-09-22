import { Component, computed, inject, input, signal } from '@angular/core';
import { BrandingService } from '../../core/services/branding.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-brand-mark',
  standalone: true,
  template: `
    <div class="flex min-w-0 items-center gap-3" [class.justify-center]="centered()">
      @if (square()) {
        <img [src]="square()!" [alt]="wordmark() ? '' : branding.displayName()" class="h-9 w-9 shrink-0 rounded-xl object-contain" (error)="squareFailed.set(square())" />
      } @else if (!wordmark()) {
        <span class="grid h-9 w-9 shrink-0 place-items-center overflow-hidden rounded-xl bg-brand-700" aria-hidden="true">
          <img src="/assets/branding/logo.png" alt="" class="h-9 w-9 object-cover" />
        </span>
      }
      @if (showName()) {
        @if (wordmark()) {
          <img [src]="wordmark()!" [alt]="branding.displayName()" class="h-8 max-w-[9rem] object-contain object-left" (error)="wordmarkFailed.set(wordmark())" />
        } @else {
          <span class="truncate font-display text-base font-semibold">{{ branding.displayName() }}</span>
        }
      }
    </div>
  `
})
export class BrandMarkComponent {
  branding = inject(BrandingService);
  private theme = inject(ThemeService);
  showName = input(true);
  centered = input(false);
  preferDark = input(false);
  squareFailed = signal<string | null>(null);
  wordmarkFailed = signal<string | null>(null);

  private useDark = computed(() => this.preferDark() || this.theme.dark());

  wordmark = computed(() => {
    if (!this.showName()) {
      return null;
    }
    const src = this.branding.wordmarkUrl(this.useDark());
    if (!src || this.wordmarkFailed() === src) {
      return null;
    }
    return src;
  });

  square = computed(() => {
    const icon = this.branding.branding()?.iconUrl || null;
    const word = this.branding.wordmarkUrl(this.useDark());
    const showingWord = !!this.wordmark();
    const src = showingWord ? icon : (icon || word || '/assets/branding/logo.png');
    if (!src || this.squareFailed() === src) {
      return null;
    }
    return src;
  });
}
