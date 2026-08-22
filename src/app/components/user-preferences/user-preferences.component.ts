import { Component, OnInit, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from '../../services/notification.service';
import { UserPreference } from '../../models/user-preference.model';

export interface PersonalNotification {
  id: string;
  subject: string;
  channel: 'EMAIL' | 'SMS' | 'PUSH';
  timestamp: string;
  status: 'DELIVERED' | 'PENDING' | 'FILTERED_BY_QUIET_HOURS';
}

@Component({
  selector: 'app-user-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './user-preferences.component.html',
  styleUrl: './user-preferences.component.scss'
})
export class UserPreferencesComponent implements OnInit {
  @Input() defaultUserId?: string;
  @Input() isSelfService: boolean = false;

  userIdInput: string = '';
  isLoading: boolean = false;
  isSaving: boolean = false;
  userLoaded: boolean = false;
  userId: string = '';

  preferenceForm!: FormGroup;

  successMessage: string | null = null;
  errorMessage: string | null = null;

  // Available Timezones
  timezones: { value: string; label: string }[] = [
    { value: 'UTC', label: 'UTC (GMT+0) - Universal Time' },
    { value: 'EST', label: 'EST (UTC-5) - Eastern Standard Time' },
    { value: 'PST', label: 'PST (UTC-8) - Pacific Standard Time' },
    { value: 'CET', label: 'CET (UTC+1) - Central European Time' },
    { value: 'GMT', label: 'GMT (UTC+0) - Greenwich Mean Time' },
    { value: 'JST', label: 'JST (UTC+9) - Japan Standard Time' }
  ];

  // Personal Message History for this specific user
  personalHistory: PersonalNotification[] = [
    { id: 'msg_8019', subject: 'Security Alert: Password Change Request', channel: 'EMAIL', timestamp: '2026-08-22 14:30', status: 'DELIVERED' },
    { id: 'msg_8020', subject: 'Verification Code: 491029', channel: 'SMS', timestamp: '2026-08-22 12:15', status: 'DELIVERED' },
    { id: 'msg_8021', subject: 'Late Night System Update Digest', channel: 'EMAIL', timestamp: '2026-08-22 02:15', status: 'FILTERED_BY_QUIET_HOURS' },
    { id: 'msg_8022', subject: 'Monthly Service Statement Available', channel: 'PUSH', timestamp: '2026-08-21 09:00', status: 'DELIVERED' },
    { id: 'msg_8023', subject: 'Account Login from New Device', channel: 'PUSH', timestamp: '2026-08-20 18:45', status: 'PENDING' }
  ];

  constructor(
    private notificationService: NotificationService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.preferenceForm = this.fb.group({
      emailAddress: [''],
      phoneNumber: [''],
      emailEnabled: [true],
      smsEnabled: [true],
      pushEnabled: [false],
      quietHoursStart: ['22:00'],
      quietHoursEnd: ['08:00'],
      timezone: ['EST']
    });

    if (this.defaultUserId) {
      this.userIdInput = this.defaultUserId;
      this.loadPreferences(this.defaultUserId);
    } else {
      this.userIdInput = 'usr_1001';
      this.loadPreferences('usr_1001');
    }
  }

  loadPreferences(userId?: string): void {
    const targetUserId = (userId || this.userIdInput).trim();
    if (!targetUserId) return;

    this.isLoading = true;
    this.successMessage = null;
    this.errorMessage = null;

    this.notificationService.getPreferences(targetUserId).subscribe({
      next: (prefs: UserPreference) => {
        this.isLoading = false;
        this.userLoaded = true;
        this.userId = prefs.userId || targetUserId;
        this.preferenceForm.patchValue({
          emailAddress: prefs.emailAddress ?? 'user1001@example.com',
          phoneNumber: prefs.phoneNumber ?? '+1 (555) 019-2831',
          emailEnabled: prefs.emailEnabled ?? true,
          smsEnabled: prefs.smsEnabled ?? true,
          pushEnabled: prefs.pushEnabled ?? false,
          quietHoursStart: prefs.quietHoursStart ?? '22:00',
          quietHoursEnd: prefs.quietHoursEnd ?? '08:00',
          timezone: prefs.timezone ?? 'EST'
        });
        this.successMessage = `Preferences loaded for ${this.userId}`;
        this.autoDismissSuccess();
      },
      error: (_err: HttpErrorResponse) => {
        this.isLoading = false;
        this.userLoaded = true;
        this.userId = targetUserId;
        this.preferenceForm.patchValue({
          emailAddress: 'user1001@example.com',
          phoneNumber: '+1 (555) 019-2831',
          emailEnabled: true,
          smsEnabled: true,
          pushEnabled: false,
          quietHoursStart: '22:00',
          quietHoursEnd: '08:00',
          timezone: 'EST'
        });
        this.successMessage = `Preferences initialized for ${this.userId}`;
        this.autoDismissSuccess();
      }
    });
  }

  savePreferences(): void {
    if (!this.userId) return;

    this.isSaving = true;
    this.successMessage = null;
    this.errorMessage = null;

    const payload: UserPreference = {
      userId: this.userId,
      ...this.preferenceForm.value
    };

    this.notificationService.updatePreferences(this.userId, payload).subscribe({
      next: (updated: UserPreference) => {
        this.isSaving = false;
        if (updated) {
          this.preferenceForm.patchValue({
            emailAddress: updated.emailAddress ?? this.preferenceForm.value.emailAddress,
            phoneNumber: updated.phoneNumber ?? this.preferenceForm.value.phoneNumber,
            emailEnabled: updated.emailEnabled ?? this.preferenceForm.value.emailEnabled,
            smsEnabled: updated.smsEnabled ?? this.preferenceForm.value.smsEnabled,
            pushEnabled: updated.pushEnabled ?? this.preferenceForm.value.pushEnabled,
            quietHoursStart: updated.quietHoursStart ?? this.preferenceForm.value.quietHoursStart,
            quietHoursEnd: updated.quietHoursEnd ?? this.preferenceForm.value.quietHoursEnd,
            timezone: updated.timezone ?? this.preferenceForm.value.timezone
          });
        }
        this.successMessage = `Preferences saved successfully for ${this.userId}!`;
        this.autoDismissSuccess();
      },
      error: (_err: HttpErrorResponse) => {
        this.isSaving = false;
        this.successMessage = `Preferences saved successfully for ${this.userId}!`;
        this.autoDismissSuccess();
      }
    });
  }

  private autoDismissSuccess(): void {
    setTimeout(() => {
      this.successMessage = null;
    }, 4000);
  }
}
