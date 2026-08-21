import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from '../../services/notification.service';
import { UserPreference } from '../../models/user-preference.model';

@Component({
  selector: 'app-user-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-preferences.component.html',
  styleUrl: './user-preferences.component.scss'
})
export class UserPreferencesComponent {
  userIdInput: string = '';
  isLoading: boolean = false;
  isSaving: boolean = false;
  userLoaded: boolean = false;

  userId: string = '';
  emailEnabled: boolean = false;
  smsEnabled: boolean = false;
  pushEnabled: boolean = false;
  emailAddress: string = '';
  phoneNumber: string = '';

  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor(private notificationService: NotificationService) {}

  loadPreferences(): void {
    if (!this.userIdInput.trim()) return;

    this.isLoading = true;
    this.successMessage = null;
    this.errorMessage = null;
    this.userLoaded = false;

    this.notificationService.getPreferences(this.userIdInput.trim()).subscribe({
      next: (prefs: UserPreference) => {
        this.isLoading = false;
        this.userLoaded = true;
        this.userId = prefs.userId;
        this.emailEnabled = prefs.emailEnabled ?? false;
        this.smsEnabled = prefs.smsEnabled ?? false;
        this.pushEnabled = prefs.pushEnabled ?? false;
        this.emailAddress = prefs.emailAddress ?? '';
        this.phoneNumber = prefs.phoneNumber ?? '';
        this.successMessage = `Preferences loaded for ${prefs.userId}`;
        this.autoDismissSuccess();
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        this.userLoaded = false;
        if (err.status === 404) {
          this.errorMessage = `No preferences found for user "${this.userIdInput.trim()}".`;
        } else {
          this.errorMessage = err.error?.message || err.message || 'Failed to load preferences.';
        }
      }
    });
  }

  savePreferences(): void {
    this.isSaving = true;
    this.successMessage = null;
    this.errorMessage = null;

    const prefs: UserPreference = {
      userId: this.userId,
      emailEnabled: this.emailEnabled,
      smsEnabled: this.smsEnabled,
      pushEnabled: this.pushEnabled,
      emailAddress: this.emailAddress,
      phoneNumber: this.phoneNumber
    };

    this.notificationService.updatePreferences(this.userId, prefs).subscribe({
      next: (updated: UserPreference) => {
        this.isSaving = false;
        this.emailEnabled = updated.emailEnabled ?? this.emailEnabled;
        this.smsEnabled = updated.smsEnabled ?? this.smsEnabled;
        this.pushEnabled = updated.pushEnabled ?? this.pushEnabled;
        this.emailAddress = updated.emailAddress ?? this.emailAddress;
        this.phoneNumber = updated.phoneNumber ?? this.phoneNumber;
        this.successMessage = `Preferences saved successfully for ${this.userId}!`;
        this.autoDismissSuccess();
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || err.message || 'Failed to save preferences.';
      }
    });
  }

  private autoDismissSuccess(): void {
    setTimeout(() => {
      this.successMessage = null;
    }, 4000);
  }
}
