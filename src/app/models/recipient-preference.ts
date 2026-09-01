export interface RecipientPreference {
  id?: number;
  recipientId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
  whatsappEnabled: boolean;
  quietHoursStart?: string; // Format: "HH:mm:ss" or "HH:mm"
  quietHoursEnd?: string;
}
