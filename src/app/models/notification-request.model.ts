export interface NotificationRequest {
  userId: string;
  channel: string;
  channelType?: string;
  priorityLevel?: string;
  subject: string;
  title?: string;
  body: string;
}
