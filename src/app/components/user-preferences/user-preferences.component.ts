import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from '../../services/notification.service';
import { UserPreference } from '../../models/user-preference.model';

@Component({
  selector: 'app-user-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './user-preferences.component.html',
  styleUrl: './user-preferences.component.scss'
})
export class UserPreferencesComponent implements OnInit {
  userIdInput: string = '';
  isLoading: boolean = false;
  isSaving: boolean = false;
  userLoaded: boolean = false;
  userId: string = '';

  preferenceForm!: FormGroup;

  successMessage: string | null = null;
  errorMessage: string | null = null;

  constructor(
    private notificationService: NotificationService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.preferenceForm = this.fb.group({
      emailAddress: [''],
      phoneNumber: [''],
      emailEnabled: [false],
      smsEnabled: [false],
      pushEnabled: [false]
    });
  }

  loadPreferences(userId?: string): void {
    const targetUserId = (userId || this.userIdInput).trim();
    if (!targetUserId) return;

    this.isLoading = true;
    this.successMessage = null;
    this.errorMessage = null;
    this.userLoaded = false;

    this.notificationService.getPreferences(targetUserId).subscribe({
      next: (prefs: UserPreference) => {
        this.isLoading = false;
        this.userLoaded = true;
        this.userId = prefs.userId || targetUserId;
        this.preferenceForm.patchValue({
          emailAddress: prefs.emailAddress ?? '',
          phoneNumber: prefs.phoneNumber ?? '',
          emailEnabled: prefs.emailEnabled ?? false,
          smsEnabled: prefs.smsEnabled ?? false,
          pushEnabled: prefs.pushEnabled ?? false
        });
        this.successMessage = `Preferences loaded for ${this.userId}`;
        this.autoDismissSuccess();
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        this.userLoaded = false;
        if (err.status === 404) {
          this.errorMessage = `No preferences found for user "${targetUserId}".`;
        } else {
          this.errorMessage = err.error?.message || err.message || 'Failed to load preferences.';
        }
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
            pushEnabled: updated.pushEnabled ?? this.preferenceForm.value.pushEnabled
          });
        }
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
