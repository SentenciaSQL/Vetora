import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  mode = signal<ThemeMode>(this.read());
  dark = signal(false);

  constructor() {
    this.apply(this.mode());
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (this.mode() === 'system') {
        this.apply('system');
      }
    });
  }

  set(mode: ThemeMode): void {
    const resolved: ThemeMode = mode === 'light' || mode === 'dark' || mode === 'system' ? mode : 'system';
    this.mode.set(resolved);
    localStorage.setItem('animalin.theme', resolved);
    this.apply(resolved);
  }

  private apply(mode: ThemeMode): void {
    const dark = mode === 'dark' || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    this.dark.set(dark);
    document.documentElement.classList.toggle('dark', dark);
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  }

  isDark(): boolean {
    return this.dark();
  }

  private read(): ThemeMode {
    const value = localStorage.getItem('animalin.theme');
    return value === 'light' || value === 'dark' || value === 'system' ? value : 'system';
  }
}
