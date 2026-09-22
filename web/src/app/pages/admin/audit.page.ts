import { Component } from '@angular/core';
import { AuditBrowserComponent } from '../clinic/audit/audit-browser.component';

@Component({
  standalone: true,
  imports: [AuditBrowserComponent],
  template: `<app-audit-browser [admin]="true" />`
})
export class AdminAuditPage {}
