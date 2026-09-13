import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { KeycloakService } from '../../core/auth/keycloak.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  templateUrl: './sidebar.component.html'
})
export class SidebarComponent {
  private keycloakService = inject(KeycloakService);

  /** Real logged-in user, replacing the old hardcoded "Admin User" /
   *  admin@enterprise.com placeholders in the template. Prefers the
   *  Account-service firstName/lastName (fullName) over the bare
   *  preferred_username, since "First Last" is what actually reads as a
   *  profile rather than a login handle — KeycloakService.fullName already
   *  falls back to username, then to a neutral label, if those aren't set. */
  get displayName(): string {
    return this.keycloakService.fullName;
  }

  get displayEmail(): string | undefined {
    return this.keycloakService.email;
  }

  /** Triggers keycloak-js's logout redirect (KeycloakService.logout()),
   *  which clears the Keycloak session and lands back on the app origin —
   *  provideAppInitializer then re-runs init() on that fresh load and,
   *  finding no session, redirects straight to the login page again. */
  logout(): void {
    this.keycloakService.logout();
  }
}
