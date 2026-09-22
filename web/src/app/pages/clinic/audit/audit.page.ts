import { Component } from '@angular/core';
import { AuditBrowserComponent } from './audit-browser.component';

@Component({
  standalone: true,
  imports: [AuditBrowserComponent],
  template: `<app-audit-browser />`
})
export class ClinicAuditPage {}
