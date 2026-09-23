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
  tenantStatus?: string | null;
  role?: string;
  roles: string[];
  permissions: string[];
  emailVerified?: boolean;
  signupStatus?: string | null;
  onboardingComplete?: boolean;
  accessGranted?: boolean;
  checkoutPending?: boolean;
  memberships: TenantSummary[];
  avatarUrl?: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface SessionConfig {
  inactivityTimeoutMinutes: number;
  warningBeforeMinutes: number;
}

export interface PublicClinic {
  slug: string;
  name: string;
  commercialName?: string;
  city?: string;
  country?: string;
  logoUrl?: string;
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
  description?: string;
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
  monthlyEquivalent?: number | null;
  savingsPercent?: number | null;
  monthlyAvailable?: boolean;
  annualAvailable?: boolean;
  paddleMonthlyPriceId?: string | null;
  paddleAnnualPriceId?: string | null;
  enabled: boolean;
  limits: PlanLimits;
  monthlyTrialDays?: number;
}

export interface BillingConfig {
  environment: 'sandbox' | 'production';
  clientToken: string;
  gracePeriodDays: number;
  trialDays?: number;
  plans: BillingPlan[];
}

export interface PublicPlan {
  id: number;
  code: string;
  name: string;
  nameEs?: string;
  nameEn?: string;
  description?: string;
  currency: string;
  monthlyPrice: number;
  annualPrice?: number | null;
  monthlyEquivalent?: number | null;
  savingsPercent?: number | null;
  monthlyAvailable?: boolean;
  annualAvailable?: boolean;
  enabled: boolean;
  limits: PlanLimits;
  monthlyTrialDays?: number;
}

export interface SignupConfig {
  environment: 'sandbox' | 'production';
  clientToken: string;
  gracePeriodDays: number;
  trialDays: number;
  maxClinicsPerOwner: number;
  defaultCountry: string;
  defaultTimezone: string;
  defaultCurrency: string;
  plans: PublicPlan[];
}

export interface SignupStatus {
  signupStatus: string;
  emailVerified: boolean;
  tenantId?: number | null;
  tenantName?: string | null;
  tenantSlug?: string | null;
  tenantStatus?: string | null;
  planId?: number | null;
  planCode?: string | null;
  planName?: string | null;
  billingCycle?: string | null;
  price?: number | null;
  currency?: string | null;
  trialDays?: number | null;
  estimatedFirstChargeAt?: string | null;
  subscriptionStatus?: string | null;
  checkoutReady: boolean;
  accessGranted: boolean;
  onboardingComplete?: boolean;
  checkoutPending?: boolean;
  limits?: PlanLimits | null;
  user?: UserProfile;
}

export interface StaffInvite {
  id: number;
  email: string;
  role: string;
  status: string;
  expiresAt?: string;
  createdAt?: string;
  firstName?: string;
  lastName?: string;
  specialtyCode?: string | null;
  specialtyOther?: string | null;
  branchId?: number | null;
}

export interface TeamMember {
  membershipId: number;
  userId: number;
  employeeId?: number | null;
  veterinarianId?: number | null;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone?: string | null;
  role: string;
  status: string;
  branchId?: number | null;
  branchName?: string | null;
  specialtyCode?: string | null;
  specialty?: string | null;
  specialtyOther?: string | null;
  owner?: boolean;
  avatarUrl?: string | null;
}

export interface UserMembershipSummary {
  id: number;
  tenantId: number;
  tenantName: string;
  role: string;
  status: string;
  branchId?: number | null;
}

export interface PlatformUser {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone?: string | null;
  enabled: boolean;
  status: string;
  locale?: string;
  emailVerified?: boolean;
  roles: string[];
  memberships: UserMembershipSummary[];
  avatarUrl?: string | null;
}

export interface InvitePreview {
  email: string;
  role: string;
  tenantName: string;
  expiresAt?: string;
  expired: boolean;
  accepted: boolean;
}

export interface UsageMetric {
  current: number;
  limit: number;
}

export interface PlanUsage {
  users: UsageMetric;
  veterinarians: UsageMetric;
  branches: UsageMetric;
  storageMb: UsageMetric;
  messagesMonth: UsageMetric;
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
  paddleProductId?: string | null;
  paddlePriceId?: string | null;
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
  hasPaddleSubscription?: boolean;
  trialAccess?: boolean;
  limits?: PlanLimits;
  usage?: PlanUsage;
  pendingPlanId?: number | null;
  pendingPlanCode?: string | null;
  pendingPlanName?: string | null;
  pendingPriceId?: string | null;
  pendingBillingInterval?: string | null;
  pendingChangeEffectiveAt?: string | null;
  pendingChangeCreatedAt?: string | null;
  pendingChangeStatus?: string | null;
  pendingChangeMessage?: string | null;
}

export interface AdminPlan {
  id?: number;
  code: string;
  nameEs: string;
  nameEn: string;
  descriptionEs?: string;
  descriptionEn?: string;
  currency: string;
  monthlyPrice: number;
  annualPrice?: number | null;
  paddleProductId?: string | null;
  paddleMonthlyPriceId?: string | null;
  paddleAnnualPriceId?: string | null;
  paddleMonthlyPriceStatus?: string | null;
  paddleAnnualPriceStatus?: string | null;
  paddleLastSyncedAt?: string | null;
  paddleSyncStatus?: string;
  active: boolean;
  subscriberCount?: number;
  limits?: PlanLimits;
  maxUsers: number;
  maxVeterinarians: number;
  maxBranches: number;
  maxStorageMb: number;
  maxMessagesMonth: number;
  reportsEnabled: boolean;
  messagingEnabled: boolean;
  laboratoryEnabled: boolean;
}

export interface PaddlePriceDiff {
  cycle: string;
  priceId: string;
  localAmount: number;
  paddleAmount: number;
  currency: string;
  interval: string;
  status: string;
  matches: boolean;
  message?: string;
}

export interface PaddleSyncResult {
  plan: AdminPlan;
  differences: PaddlePriceDiff[];
  inSync: boolean;
  environment: string;
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

export interface ChangePreview {
  currentPlanId?: number | null;
  currentPlanCode?: string | null;
  currentPlanName?: string | null;
  currentCycle?: string | null;
  newPlanId: number;
  newPlanCode: string;
  newPlanName: string;
  newCycle: string;
  estimatedAmount?: number | null;
  currency: string;
  nextBillingAt?: string | null;
  prorationMode?: string;
  currentPlan?: string | null;
  newPlan?: string | null;
  changeType?: 'UPGRADE' | 'DOWNGRADE' | 'CYCLE_CHANGE' | string;
  effectiveAt?: string | null;
  immediateCharge?: number | null;
  credit?: number | null;
  billingInterval?: string | null;
  message?: string | null;
}

export interface ConversationSummary {
  id: number;
  subject?: string;
  tenantId?: number;
  tenantName?: string;
  petName?: string;
  ownerName?: string;
  title?: string;
  lastMessage?: string;
  updatedAt?: string;
  unread: number;
}

export interface ChatMessage {
  id: number;
  senderId: number;
  senderName: string;
  body: string;
  createdAt: string;
  readAt?: string | null;
}

export interface UnreadCount {
  count: number;
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
    resource?: string;
    current?: number;
    limit?: number;
    plan?: string;
    feature?: string;
  };
}
