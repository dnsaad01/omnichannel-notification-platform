import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PreferenceService } from '../../services/preference.service';
import { RecipientPreference } from '../../models/recipient-preference';

@Component({
  selector: 'app-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './preferences.component.html',
  styleUrls: ['./preferences.component.scss']
})
export class PreferencesComponent {
  searchRecipientId: string = '';
  isLoading: boolean = false;
  successMessage: string = '';
  errorMessage: string = '';

  preference: RecipientPreference = {
    recipientId: '',
    emailEnabled: true,
    smsEnabled: true,
    pushEnabled: true,
    whatsappEnabled: true,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00'
  };

  constructor(private preferenceService: PreferenceService) {}

  onSearch(): void {
    if (!this.searchRecipientId.trim()) return;

    this.isLoading = true;
    this.resetAlerts();

    this.preferenceService.getPreferences(this.searchRecipientId.trim()).subscribe({
      next: (data) => {
        this.preference = data;
        this.isLoading = false;
      },
      error: () => {
        this.preference = {
          recipientId: this.searchRecipientId.trim(),
          emailEnabled: true,
          smsEnabled: true,
          pushEnabled: true,
          whatsappEnabled: true,
          quietHoursStart: '22:00',
          quietHoursEnd: '08:00'
        };
        this.isLoading = false;
      }
    });
  }

  onSave(): void {
    if (!this.preference.recipientId) return;

    this.isLoading = true;
    this.resetAlerts();

    this.preferenceService.savePreferences(this.preference).subscribe({
      next: (savedData) => {
        this.preference = savedData;
        this.isLoading = false;
        this.successMessage = 'Preferences updated successfully.';
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Failed to save recipient preferences.';
      }
    });
  }

  private resetAlerts(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }
}
