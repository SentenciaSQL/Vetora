import { inject, Injectable, signal } from '@angular/core';
import { Branding } from '../models';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class BrandingService {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  branding = signal<Branding | null>(null);

  loadForSession(): void {
    if (this.auth.isSuperAdmin() || !this.auth.user()?.tenantId) {
      this.branding.set({
        name: 'Vetora',
        commercialName: 'Vetora',
        logoUrl: '/assets/branding/logo.png',
        iconUrl: '/assets/branding/logo.png',
        primaryLanguage: 'es'
      });
      return;
    }
    this.api.get<Branding>('/settings/branding').subscribe({
      next: value => this.branding.set(value),
      error: () => this.branding.set({ name: 'Vetora', commercialName: 'Vetora', logoUrl: '/assets/branding/logo.png' })
    });
  }

  loadPublic(slug: string) {
    return this.api.get<Branding>(`/public/tenants/${slug}/branding`);
  }

  displayName(): string {
    const brand = this.branding();
    return brand?.commercialName || brand?.name || 'Vetora';
  }

  logoUrl(dark: boolean): string | null {
    const brand = this.branding();
    if (!brand) {
      return '/assets/branding/logo.png';
    }
    if (dark && brand.darkLogoUrl) {
      return brand.darkLogoUrl;
    }
    return brand.logoUrl || brand.iconUrl || '/assets/branding/logo.png';
  }
}
