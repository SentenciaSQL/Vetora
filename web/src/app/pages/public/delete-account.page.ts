import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { LanguageSelectorComponent } from '../../shared/ui/language-selector.component';

interface Copy {
  title: string;
  intro: string[];
  appTitle: string;
  steps: string[];
  webTitle: string;
  webBody: string;
  email: string;
  reason: string;
  optional: string;
  confirm: string;
  submit: string;
  ackHint: string;
  confirmTitle: string;
  confirmBody: string;
  confirmButton: string;
  deletedTitle: string;
  deletedIntro: string;
  deleted: string[];
  deletedNote: string;
  keptTitle: string;
  keptIntro: string;
  kept: string[];
  keptNote: string;
  operational: string;
  ownerTitle: string;
  owner: string;
  updated: string;
  appName: string;
  privacy: string;
  terms: string;
  contact: string;
  genericError: string;
  noscript: string;
}

const ES: Copy = {
  title: 'Eliminación de cuenta y datos de LunaVeta',
  intro: [
    'Los usuarios de LunaVeta pueden solicitar en cualquier momento la eliminación de su cuenta y de los datos personales asociados.',
    'Puede iniciar la solicitud desde la aplicación, en Perfil > Seguridad y privacidad > Eliminar mi cuenta, o utilizar el formulario disponible en esta página.',
    'Para proteger su cuenta, enviaremos un enlace de confirmación al correo electrónico registrado. La solicitud no será procesada hasta que se verifique dicho correo.',
    'Al completarse el proceso, eliminaremos o anonimizaremos la información personal que ya no sea necesaria, incluyendo los datos del perfil, credenciales, sesiones, dispositivos y preferencias.',
    'Algunos registros clínicos, fiscales, de seguridad y auditoría podrán conservarse durante el periodo requerido por las obligaciones legales aplicables o mientras sean necesarios para proteger la integridad y seguridad del servicio. Cuando sea posible, se conservarán de manera anonimizada.',
    'Si usted es el único propietario de una veterinaria activa o mantiene una suscripción pendiente, es posible que deba transferir la propiedad, cerrar la veterinaria o resolver la suscripción antes de completar la eliminación.'
  ],
  appTitle: 'Eliminar la cuenta desde la aplicación',
  steps: [
    'Inicie sesión en LunaVeta.',
    'Abra la sección Perfil.',
    'Seleccione Seguridad y privacidad.',
    'Presione Eliminar mi cuenta.',
    'Lea las consecuencias de la eliminación.',
    'Escriba su contraseña actual.',
    'Escriba la palabra ELIMINAR.',
    'Confirme la solicitud.',
    'Al completarse la eliminación, todas las sesiones serán cerradas.'
  ],
  webTitle: 'Solicitar la eliminación desde la web',
  webBody: 'Este formulario no elimina la cuenta de inmediato. Si existe una cuenta con el correo indicado, enviaremos un enlace de un solo uso para confirmar la solicitud. El enlace caduca a las 24 horas. Ese plazo es operativo del sistema y no es un periodo legal.',
  email: 'Correo electrónico asociado a la cuenta',
  reason: 'Motivo de la solicitud',
  optional: 'opcional',
  confirm: 'Confirmo que deseo eliminar la cuenta y los datos asociados.',
  submit: 'Enviar solicitud',
  ackHint: 'La respuesta no indica si el correo está registrado.',
  confirmTitle: 'Confirmar la solicitud',
  confirmBody: 'Abrió un enlace de confirmación. Pulse el botón para verificar el correo y continuar. El enlace es de un solo uso.',
  confirmButton: 'Confirmar la eliminación de mi cuenta',
  deletedTitle: 'Datos que se eliminarán o anonimizarán',
  deletedIntro: 'Una vez aprobada y procesada la solicitud, se eliminarán o anonimizarán, según corresponda:',
  deleted: [
    'Nombre y apellidos.',
    'Correo electrónico.',
    'Número de teléfono.',
    'Fotografía de perfil.',
    'Dirección personal, si existe.',
    'Credenciales de acceso.',
    'Tokens de sesión y recuperación.',
    'Tokens de notificaciones push.',
    'Preferencias personales.',
    'Identificadores de dispositivos.',
    'Información del perfil que no sea necesaria conservar.',
    'Datos de marketing y comunicaciones opcionales.',
    'Datos personales contenidos en conversaciones, cuando puedan eliminarse o anonimizarse sin afectar obligaciones clínicas o legales.'
  ],
  deletedNote: 'La eliminación física inmediata de todos los registros no se promete cuando el sistema debe conservar algunos por obligaciones clínicas, financieras, de seguridad o de integridad referencial.',
  keptTitle: 'Datos que pueden conservarse',
  keptIntro: 'Algunos datos pueden conservarse de manera limitada cuando sea necesario:',
  kept: [
    'Historiales clínicos de mascotas.',
    'Diagnósticos y tratamientos.',
    'Vacunas.',
    'Recetas.',
    'Resultados de laboratorio.',
    'Citas asociadas a registros clínicos.',
    'Facturas, pagos y transacciones.',
    'Información necesaria para obligaciones fiscales.',
    'Registros de auditoría y seguridad.',
    'Evidencia de consentimientos.',
    'Información necesaria para resolver disputas o prevenir fraude.'
  ],
  keptNote: 'Cuando sea posible, estos registros se anonimizan o se desvinculan de la identidad personal eliminada. Algunos registros clínicos, fiscales, de seguridad y auditoría podrán conservarse durante el periodo requerido por las obligaciones legales aplicables o mientras sean necesarios para proteger la integridad y seguridad del servicio. Cuando sea posible, se conservarán de manera anonimizada.',
  operational: 'Plazos operativos del sistema, distintos de los periodos legales: el enlace de confirmación caduca a las 24 horas y las solicitudes no verificadas se eliminan a los 30 días. Los registros técnicos sin información sensible siguen la política vigente del sistema.',
  ownerTitle: 'Propietarios de una veterinaria',
  owner: 'Si usted es el único propietario de una veterinaria activa, la cuenta no se elimina de forma automática. Debe transferir la propiedad o cerrar la veterinaria. Si hay una suscripción pendiente, debe resolverla desde la aplicación web antes de completar la eliminación. La aplicación móvil no ofrece comprar, cambiar ni cancelar planes. Escriba a supportlunaveta@gmail.com. La solicitud queda registrada.',
  updated: 'Última actualización: 21 de septiembre de 2026.',
  appName: 'Aplicación: LunaVeta.',
  privacy: 'Política de privacidad',
  terms: 'Términos y condiciones',
  contact: 'Contacto o soporte',
  genericError: 'No se pudo completar la solicitud. Inténtelo de nuevo.',
  noscript: 'Puede leer esta página y enviar el formulario sin JavaScript. Para confirmar el enlace recibido por correo, abra esta dirección en un navegador con JavaScript o escriba a supportlunaveta@gmail.com desde el correo de la cuenta.'
};

const EN: Copy = {
  title: 'LunaVeta account and data deletion',
  intro: [
    'LunaVeta users can request deletion of their account and associated personal data at any time.',
    'You can start from the app at Profile > Security and privacy > Delete my account, or use the form on this page.',
    'To protect your account, we email a confirmation link to the registered address. The request is not processed until that email is verified.',
    'When the process is complete, we delete or anonymize personal information that is no longer needed, including profile data, credentials, sessions, devices, and preferences.',
    'Some clinical, tax, security, and audit records may be kept for the period required by applicable legal obligations or while they are needed to protect the integrity and security of the service. When possible, they are kept in anonymized form.',
    'If you are the only owner of an active clinic or have an outstanding subscription, you may need to transfer ownership, close the clinic, or resolve the subscription before deletion can be completed.'
  ],
  appTitle: 'Delete the account from the app',
  steps: [
    'Sign in to LunaVeta.',
    'Open Profile.',
    'Select Security and privacy.',
    'Press Delete my account.',
    'Read the consequences of deletion.',
    'Enter your current password.',
    'Type the word ELIMINAR.',
    'Confirm the request.',
    'When deletion is complete, all sessions are closed.'
  ],
  webTitle: 'Request deletion on the web',
  webBody: 'This form does not delete the account immediately. If an account exists for the email you enter, we send a one-time link to confirm the request. The link expires after 24 hours. That period is operational and is not a legal retention period.',
  email: 'Email address for the account',
  reason: 'Reason for the request',
  optional: 'optional',
  confirm: 'I confirm that I want to delete the account and the associated data.',
  submit: 'Send request',
  ackHint: 'The response does not say whether the email is registered.',
  confirmTitle: 'Confirm the request',
  confirmBody: 'You opened a confirmation link. Press the button to verify the email and continue. The link can be used only once.',
  confirmButton: 'Confirm deletion of my account',
  deletedTitle: 'Data that will be deleted or anonymized',
  deletedIntro: 'Once the request is approved and processed, the following will be deleted or anonymized, as applicable:',
  deleted: [
    'First and last name.',
    'Email address.',
    'Phone number.',
    'Profile photo.',
    'Personal address, if any.',
    'Sign-in credentials.',
    'Session and recovery tokens.',
    'Push notification tokens.',
    'Personal preferences.',
    'Device identifiers.',
    'Profile information that does not need to be kept.',
    'Marketing data and optional communications.',
    'Personal data in conversations, when it can be deleted or anonymized without affecting clinical or legal duties.'
  ],
  deletedNote: 'Immediate physical deletion of every record is not promised when some records must be kept for clinical, financial, security, or referential-integrity duties.',
  keptTitle: 'Data that may be retained',
  keptIntro: 'Some data may be kept in a limited way when necessary:',
  kept: [
    'Pet clinical histories.',
    'Diagnoses and treatments.',
    'Vaccines.',
    'Prescriptions.',
    'Laboratory results.',
    'Appointments linked to clinical records.',
    'Invoices, payments, and transactions.',
    'Information required for tax duties.',
    'Audit and security records.',
    'Evidence of consent.',
    'Information needed to resolve disputes or prevent fraud.'
  ],
  keptNote: 'When possible, these records are anonymized or unlinked from the deleted personal identity. Some clinical, tax, security, and audit records may be kept for the period required by applicable legal obligations or while they are needed to protect the integrity and security of the service. When possible, they are kept in anonymized form.',
  operational: 'Operational system periods, separate from legal periods: the confirmation link expires after 24 hours and unverified requests are deleted after 30 days. Technical logs without sensitive information follow the system’s current policy.',
  ownerTitle: 'Clinic owners',
  owner: 'If you are the only owner of an active clinic, the account is not deleted automatically. Transfer ownership or close the clinic. If a subscription is outstanding, resolve it from the web app before deletion can be completed. The mobile app does not sell, change, or cancel plans. Write to supportlunaveta@gmail.com. The request stays on record.',
  updated: 'Last updated: 21 September 2026.',
  appName: 'Application: LunaVeta.',
  privacy: 'Privacy policy',
  terms: 'Terms and conditions',
  contact: 'Contact or support',
  genericError: 'The request could not be completed. Try again.',
  noscript: 'You can read this page and submit the form without JavaScript. To confirm the link sent by email, open this address in a browser with JavaScript or write to supportlunaveta@gmail.com from the account email.'
};

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, LanguageSelectorComponent],
  template: `
    <div class="mx-auto min-h-screen max-w-3xl px-5 py-8">
      <div class="mb-6 flex items-center justify-between">
        <p class="font-extrabold tracking-wide text-brand-800">LunaVeta</p>
        <app-language-selector />
      </div>
      <h1 class="font-display text-3xl font-semibold leading-tight">{{ copy().title }}</h1>
      @for (paragraph of copy().intro; track paragraph) {
        <p class="mt-4">{{ paragraph }}</p>
      }

      <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().appTitle }}</h2>
      <ol class="mt-3 list-decimal space-y-1 pl-5">
        @for (step of copy().steps; track step) {
          <li>{{ step }}</li>
        }
      </ol>

      <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().webTitle }}</h2>
      <p class="mt-3">{{ copy().webBody }}</p>
      <form class="card mt-4 space-y-3" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="mb-1 block text-sm font-medium" for="deletion-email">{{ copy().email }}</label>
          <input id="deletion-email" class="input" type="email" formControlName="email" autocomplete="email" maxlength="180" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium" for="deletion-reason">{{ copy().reason }} <span class="font-normal text-slate-500">({{ copy().optional }})</span></label>
          <textarea id="deletion-reason" class="input" rows="4" formControlName="reason" maxlength="500"></textarea>
        </div>
        <label class="flex items-start gap-2 text-sm" for="deletion-confirmation">
          <input id="deletion-confirmation" class="mt-1" type="checkbox" formControlName="confirmation" />
          <span>{{ copy().confirm }}</span>
        </label>
        <button class="btn-primary" type="submit" [disabled]="form.invalid || sending()">{{ copy().submit }}</button>
        @if (notice()) {
          <p class="rounded-xl bg-teal-50 px-3 py-2 text-sm text-teal-950" role="status">{{ notice() }}</p>
        }
        @if (error()) {
          <p class="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">{{ error() }}</p>
        }
      </form>
      <p class="mt-3 text-sm text-slate-500">{{ copy().ackHint }}</p>

      @if (token()) {
        <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().confirmTitle }}</h2>
        <p class="mt-3">{{ copy().confirmBody }}</p>
        <button class="btn-primary mt-4" type="button" [disabled]="confirming()" (click)="confirm()">{{ copy().confirmButton }}</button>
        @if (confirmNotice()) {
          <p class="mt-3 rounded-xl bg-teal-50 px-3 py-2 text-sm text-teal-950" role="status">{{ confirmNotice() }}</p>
        }
        @if (confirmError()) {
          <p class="mt-3 rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">{{ confirmError() }}</p>
        }
      }

      <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().deletedTitle }}</h2>
      <p class="mt-3">{{ copy().deletedIntro }}</p>
      <ul class="mt-3 list-disc space-y-1 pl-5">
        @for (item of copy().deleted; track item) {
          <li>{{ item }}</li>
        }
      </ul>
      <p class="mt-3">{{ copy().deletedNote }}</p>

      <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().keptTitle }}</h2>
      <p class="mt-3">{{ copy().keptIntro }}</p>
      <ul class="mt-3 list-disc space-y-1 pl-5">
        @for (item of copy().kept; track item) {
          <li>{{ item }}</li>
        }
      </ul>
      <p class="mt-3">{{ copy().keptNote }}</p>
      <p class="mt-3 text-sm text-slate-500">{{ copy().operational }}</p>

      <h2 class="mt-8 font-display text-xl font-semibold">{{ copy().ownerTitle }}</h2>
      <p class="mt-3">{{ copy().owner }}</p>
      <p class="mt-6">{{ copy().updated }}</p>
      <p>{{ copy().appName }}</p>
      <footer class="mt-6 flex flex-wrap gap-x-4 gap-y-2 text-sm">
        <a class="text-brand-700 hover:underline" href="/privacidad">{{ copy().privacy }}</a>
        <a class="text-brand-700 hover:underline" href="/terminos">{{ copy().terms }}</a>
        <a class="text-brand-700 hover:underline" href="mailto:supportlunaveta@gmail.com">{{ copy().contact }}</a>
      </footer>
    </div>
  `
})
export class DeleteAccountPage implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private title = inject(Title);
  private meta = inject(Meta);
  private i18n = inject(TranslateService);
  private langSub?: Subscription;

  copy = signal<Copy>(ES);
  token = signal('');
  sending = signal(false);
  confirming = signal(false);
  notice = signal('');
  error = signal('');
  confirmNotice = signal('');
  confirmError = signal('');
  form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    reason: ['', Validators.maxLength(500)],
    confirmation: [false, Validators.requiredTrue]
  });

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token') ?? '');
    this.applyLanguage(this.i18n.getCurrentLang() || 'es');
    this.langSub = this.i18n.onLangChange.subscribe(event => this.applyLanguage(event.lang));
    this.ensureCanonical();
  }

  ngOnDestroy(): void {
    this.langSub?.unsubscribe();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.notice.set('');
    this.error.set('');
    const reason = this.form.value.reason?.trim();
    this.api.post<{ message: string }>('/public/account-deletion-requests', {
      email: this.form.value.email?.trim(),
      reason: reason ? reason : null,
      confirmation: true
    }).subscribe({
      next: response => {
        this.notice.set(response.message);
        this.sending.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(error.error?.message || this.copy().genericError);
        this.sending.set(false);
      }
    });
  }

  confirm(): void {
    const token = this.token();
    if (!token) {
      return;
    }
    this.confirming.set(true);
    this.confirmNotice.set('');
    this.confirmError.set('');
    this.api.post<{ message: string }>('/public/account-deletion-requests/confirm', { token }).subscribe({
      next: response => {
        this.confirmNotice.set(response.message);
        this.confirming.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.confirmError.set(error.error?.message || this.copy().genericError);
        this.confirming.set(false);
      }
    });
  }

  private applyLanguage(lang: string): void {
    const english = lang.toLowerCase().startsWith('en');
    this.copy.set(english ? EN : ES);
    const pageTitle = english ? 'Delete account and data | LunaVeta' : 'Eliminar cuenta y datos | LunaVeta';
    const description = english
      ? 'Learn how to request deletion of your LunaVeta account and which data is deleted or may be retained.'
      : 'Consulta cómo solicitar la eliminación de tu cuenta de LunaVeta y conoce qué datos se eliminarán o podrán conservarse.';
    this.title.setTitle(pageTitle);
    this.meta.updateTag({ name: 'description', content: description });
    document.documentElement.lang = english ? 'en' : 'es';
  }

  private ensureCanonical(): void {
    let link = document.querySelector("link[rel='canonical']") as HTMLLinkElement | null;
    if (!link) {
      link = document.createElement('link');
      link.rel = 'canonical';
      document.head.appendChild(link);
    }
    link.href = 'https://lunaveta.com/eliminar-cuenta';
  }
}
