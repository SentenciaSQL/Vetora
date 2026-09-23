import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { statusLabel } from '../../core/team-labels';

@Pipe({ name: 'statusLabel', standalone: true })
export class StatusLabelPipe implements PipeTransform {
  private i18n = inject(TranslateService);

  transform(code?: string | null): string {
    return statusLabel(this.i18n, code);
  }
}
