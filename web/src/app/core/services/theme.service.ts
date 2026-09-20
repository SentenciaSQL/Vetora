import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  mode = signal<ThemeMode>(this.read());

  constructor() {
    this.apply(this.mode());
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (this.mode() === 'system') {
        this.apply('system');
      }
    });
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    localStorage.setItem('animalin.theme', mode);
    this.apply(mode);
  }

  private apply(mode: ThemeMode): void {
    const dark = mode === 'dark' || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  }

  private read(): ThemeMode {
    const value = localStorage.getItem('animalin.theme');
    return value === 'light' || value === 'dark' || value === 'system' ? value : 'system';
  }
}
