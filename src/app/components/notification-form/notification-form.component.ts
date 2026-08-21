import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from '../../services/notification.service';
import { NotificationRequest } from '../../models/notification-request.model';
import { ErrorResponse } from '../../models/error-response.model';

export interface ApiResponseLog {
  id: string;
  timestamp: Date;
  isSuccess: boolean;
  statusCode: number;
  statusText: string;
  message: string;
  errorDetail?: ErrorResponse;
  requestPayload: NotificationRequest;
}

@Component({
  selector: 'app-notification-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notification-form.component.html',
  styleUrl: './notification-form.component.scss'
})
export class NotificationFormComponent {
  apiKey: string = '';
  userId: string = 'usr_1001';
  channelType: string = 'EMAIL';
  priorityLevel: string = 'HIGH';
  title: string = 'Important System Notification';
  body: string = 'Your account notification settings have been updated successfully.';

  isLoading: boolean = false;
  latestSuccessMessage: string | null = null;
  latestError: ErrorResponse | null = null;
  responseLogs: ApiResponseLog[] = [];

  constructor(private notificationService: NotificationService) {}

  onSubmit(): void {
    this.isLoading = true;
    this.latestSuccessMessage = null;
    this.latestError = null;

    const request: NotificationRequest = {
      userId: this.userId,
      channel: this.channelType,
      channelType: this.channelType,
      priorityLevel: this.priorityLevel,
      subject: this.title,
      title: this.title,
      body: this.body
    };

    this.notificationService.sendNotification(this.apiKey, request).subscribe({
      next: (res: any) => {
        this.isLoading = false;
        const msg = res?.message || 'Notification request successfully queued for processing';
        this.latestSuccessMessage = msg;

        this.responseLogs.unshift({
          id: Math.random().toString(36).substring(2, 9),
          timestamp: new Date(),
          isSuccess: true,
          statusCode: 202,
          statusText: '202 ACCEPTED',
          message: msg,
          requestPayload: { ...request }
        });
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        let errDto: ErrorResponse;

        if (err.error && typeof err.error === 'object' && err.error.status) {
          errDto = err.error as ErrorResponse;
        } else {
          errDto = {
            timestamp: new Date().toISOString(),
            status: err.status || 500,
            error: err.statusText || 'Error',
            message: err.message || 'An unexpected error occurred',
            path: '/api/v1/notifications'
          };
        }

        this.latestError = errDto;

        this.responseLogs.unshift({
          id: Math.random().toString(36).substring(2, 9),
          timestamp: new Date(),
          isSuccess: false,
          statusCode: errDto.status,
          statusText: `${errDto.status} ${errDto.error}`,
          message: errDto.message,
          errorDetail: errDto,
          requestPayload: { ...request }
        });
      }
    });
  }

  clearLogs(): void {
    this.responseLogs = [];
    this.latestSuccessMessage = null;
    this.latestError = null;
  }
}
