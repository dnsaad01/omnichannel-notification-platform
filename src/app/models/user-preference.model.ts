export interface UserPreference {
  userId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
  emailAddress: string;
  phoneNumber: string;
  quietHoursStart?: string;
  quietHoursEnd?: string;
  timezone?: string;
}
