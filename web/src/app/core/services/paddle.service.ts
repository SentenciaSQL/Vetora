import { Injectable } from '@angular/core';
import { initializePaddle, Paddle } from '@paddle/paddle-js';
import { BillingConfig, CheckoutSession } from '../models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PaddleService {
  private paddle?: Paddle;
  private initializing?: Promise<Paddle | undefined>;

  async ensure(config?: BillingConfig | null): Promise<Paddle | undefined> {
    if (this.paddle) {
      return this.paddle;
    }
    if (this.initializing) {
      return this.initializing;
    }
    const token = config?.clientToken || environment.paddleClientToken;
    const paddleEnvironment = (config?.environment || environment.paddleEnvironment || 'sandbox') === 'production'
      ? 'production'
      : 'sandbox';
    if (!token) {
      return undefined;
    }
    this.initializing = initializePaddle({
      environment: paddleEnvironment,
      token
    }).then(instance => {
      this.paddle = instance;
      this.initializing = undefined;
      return instance;
    });
    return this.initializing;
  }

  async openCheckout(session: CheckoutSession, onComplete?: () => void, successUrl?: string): Promise<void> {
    const paddle = await this.ensure({
      environment: session.environment,
      clientToken: session.clientToken,
      gracePeriodDays: 10,
      plans: []
    });
    if (!paddle) {
      throw new Error('Paddle.js is not initialized');
    }
    paddle.Update({
      eventCallback: event => {
        if (event.name === 'checkout.completed') {
          onComplete?.();
        }
      }
    });
    paddle.Checkout.open({
      items: [{ priceId: session.priceId, quantity: 1 }],
      customData: session.customData,
      customer: session.customerEmail ? { email: session.customerEmail } : undefined,
      settings: {
        locale: session.locale || 'es',
        successUrl: successUrl || `${window.location.origin}/billing?checkout=success`
      }
    });
  }
}
