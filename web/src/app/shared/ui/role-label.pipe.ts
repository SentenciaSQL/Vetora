import { inject, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { roleLabel } from '../../core/team-labels';

@Pipe({ name: 'roleLabel', standalone: true })
export class RoleLabelPipe implements PipeTransform {
  private i18n = inject(TranslateService);

  transform(code?: string | null): string {
    return roleLabel(this.i18n, code);
  }
}
