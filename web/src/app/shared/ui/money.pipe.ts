import { Pipe, PipeTransform } from '@angular/core';
import { formatMoney } from '../../core/money';

@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: number | string | null | undefined, currency?: string | null): string {
    return formatMoney(value, currency);
  }
}
