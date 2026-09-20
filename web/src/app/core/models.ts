export interface TenantSummary {
  id: number;
  slug: string;
  name: string;
  commercialName?: string;
  role: string;
  logoUrl?: string;
}

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone?: string;
  locale?: string;
  theme?: string;
  tenantId?: number | null;
  tenantName?: string;
  tenantSlug?: string;
  role?: string;
  roles: string[];
  permissions: string[];
  memberships: TenantSummary[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface Branding {
  tenantId?: number;
  slug?: string;
  name: string;
  commercialName?: string;
  logoUrl?: string | null;
  darkLogoUrl?: string | null;
  iconUrl?: string | null;
  email?: string;
  phone?: string;
  address?: string;
  city?: string;
  country?: string;
  website?: string;
  instagram?: string;
  facebook?: string;
  timezone?: string;
  currency?: string;
  primaryLanguage?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Owner {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  documentId?: string;
  phone?: string;
  email?: string;
  address?: string;
  city?: string;
  country?: string;
  notes?: string;
  status?: string;
  petCount?: number;
}

export interface Pet {
  id: number;
  name: string;
  species?: string;
  breed?: string;
  sex?: string;
  birthDate?: string;
  age?: string;
  weightKg?: number;
  color?: string;
  microchip?: string;
  reproductiveStatus?: string;
  sterilized?: boolean;
  allergies?: string;
  medicalConditions?: string;
  notes?: string;
  photoUrl?: string;
  status?: string;
  ownerId?: number;
  ownerName?: string;
  veterinarianId?: number;
  veterinarianName?: string;
  branchId?: number;
  tenantId?: number;
  tenantName?: string;
  tenantLogoUrl?: string;
}

export interface Appointment {
  id: number;
  petId: number;
  petName: string;
  ownerId: number;
  ownerName: string;
  veterinarianId?: number;
  veterinarianName?: string;
  serviceId?: number;
  serviceName?: string;
  branchId?: number;
  startAt: string;
  endAt?: string;
  durationMin: number;
  reason?: string;
  notes?: string;
  status: string;
  tenantId?: number;
  tenantName?: string;
  tenantLogoUrl?: string;
}

export interface TimelineEvent {
  type: string;
  at: string;
  title: string;
  summary?: string;
  status?: string;
  veterinarianName?: string;
  entityId?: number;
}

export interface SearchResult {
  pets: { id: number; name: string; species: string; owner: string }[];
  owners: { id: number; name: string; email: string; phone: string }[];
  veterinarians: { id: number; name: string; specialty: string }[];
}

export interface PlanLimits {
  maxUsers: number;
  maxVeterinarians: number;
  maxBranches: number;
  maxStorageMb: number;
  maxMessagesMonth: number;
  reportsEnabled: boolean;
  messagingEnabled: boolean;
  laboratoryEnabled: boolean;
}

export interface BillingPlan {
  id: number;
  code: string;
  name: string;
  nameEs?: string;
  nameEn?: string;
  description?: string;
  descriptionEs?: string;
  descriptionEn?: string;
  currency: string;
  monthlyPrice: number;
  annualPrice?: number | null;
  paddleMonthlyPriceId?: string | null;
  paddleAnnualPriceId?: string | null;
  enabled: boolean;
  limits: PlanLimits;
}

export interface BillingConfig {
  environment: 'sandbox' | 'production';
  clientToken: string;
  gracePeriodDays: number;
  plans: BillingPlan[];
}

export interface TenantSubscription {
  id?: number;
  tenantId: number;
  planId?: number;
  planCode?: string;
  planName?: string;
  status: string;
  billingCycle?: string | null;
  currency: string;
  trial: boolean;
  startedAt?: string | null;
  currentPeriodStartsAt?: string | null;
  currentPeriodEndsAt?: string | null;
  nextBillingAt?: string | null;
  canceledAt?: string | null;
  lastPaymentSucceededAt?: string | null;
  firstPaymentFailedAt?: string | null;
  gracePeriodEndsAt?: string | null;
  suspendedAt?: string | null;
  scheduledChangeAction?: string | null;
  scheduledChangeEffectiveAt?: string | null;
  accessGranted: boolean;
  gracePeriod: boolean;
  suspended: boolean;
  hasPaddleCustomer: boolean;
}

export interface CheckoutSession {
  environment: 'sandbox' | 'production';
  clientToken: string;
  priceId: string;
  billingCycle: string;
  customData: Record<string, string>;
  customerEmail: string;
  locale: string;
}

export interface PortalSession {
  url: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  code: string;
  message: string;
  path?: string;
  details?: {
    status?: string;
    gracePeriodEndsAt?: string;
    suspendedAt?: string;
  };
}
