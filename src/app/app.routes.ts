import { Routes } from '@angular/router';
import { NotificationFormComponent } from './components/notification-form/notification-form.component';
import { PreferencesComponent } from './components/preferences/preferences.component';
// أو UserPreferencesComponent إذا كانت هي المعتمدة

export const routes: Routes = [
  { path: '', redirectTo: 'notifications', pathMatch: 'full' },
  { path: 'notifications', component: NotificationFormComponent },
  { path: 'preferences', component: PreferencesComponent }
];
