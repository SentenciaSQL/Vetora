import { Component, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

@Component({
  standalone: true,
  template: `<iframe class="block h-screen w-full border-0 bg-sand-50" [src]="src" [attr.title]="heading"></iframe>`
})
export class PublicLegalPage {
  private route = inject(ActivatedRoute);
  private title = inject(Title);
  private sanitizer = inject(DomSanitizer);
  heading = String(this.route.snapshot.data['title'] || 'LunaVeta');
  src: SafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(String(this.route.snapshot.data['src']));

  constructor() {
    this.title.setTitle(this.heading);
  }
}
