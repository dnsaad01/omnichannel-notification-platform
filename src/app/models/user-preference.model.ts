export interface UserPreference {
  userId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
  emailAddress: string;
  phoneNumber: string;
}
