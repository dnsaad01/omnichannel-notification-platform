import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { KeycloakService } from '../../core/auth/keycloak.service';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let keycloakServiceSpy: jasmine.SpyObj<KeycloakService>;

  beforeEach(async () => {
    // fullName/email are getters on the real KeycloakService — the object
    // literal form of createSpyObj's third argument stubs them as fixed
    // values, while 'logout' becomes an actual jasmine.Spy so calls to it
    // can be asserted on below.
    keycloakServiceSpy = jasmine.createSpyObj<KeycloakService>(
      'KeycloakService',
      ['logout'],
      { fullName: 'Jane Doe', email: 'jane.doe@example.com' }
    );

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        // SidebarComponent's template uses routerLink/routerLinkActive,
        // which need a Router in the injector even though no navigation
        // actually happens in this test.
        provideRouter([]),
        { provide: KeycloakService, useValue: keycloakServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
  });

  it('should render the real logged-in user\'s name and email instead of the old hardcoded placeholders', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Jane Doe');
    expect(text).toContain('jane.doe@example.com');
    expect(text).not.toContain('Admin User');
    expect(text).not.toContain('admin@enterprise.com');
  });

  it('should call KeycloakService.logout() when the Déconnexion button is clicked', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button).withContext('logout button should be rendered').not.toBeNull();

    button.click();

    expect(keycloakServiceSpy.logout).toHaveBeenCalledTimes(1);
  });
});
