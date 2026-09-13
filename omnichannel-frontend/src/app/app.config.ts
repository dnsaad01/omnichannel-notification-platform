import { ApplicationConfig, importProvidersFrom, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Bell,
  ChartColumn,
  Circle,
  CircleCheckBig,
  Compass,
  Eraser,
  FileText,
  Flag,
  FlaskConical,
  Hourglass,
  Key,
  LayoutDashboard,
  LogOut,
  Mail,
  Monitor,
  MessageCircle,
  Pause,
  PartyPopper,
  Pencil,
  Play,
  Plus,
  Radio,
  RefreshCw,
  Rocket,
  Save,
  Search,
  Send,
  Settings,
  Shuffle,
  Square,
  Timer,
  Trash2,
  TrendingUp,
  TriangleAlert,
  User,
  X,
  Zap
} from 'lucide-angular';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { KeycloakService } from './core/auth/keycloak.service';

/** Set unique d'icônes utilisé dans toute l'application (sidebar, dashboard,
 *  formulaire de notification, simulateur d'événements, monitoring, builder
 *  de workflows, etc.). Enregistré une seule fois ici via
 *  LucideAngularModule.pick() ; chaque composant standalone qui affiche une
 *  icône importe ensuite simplement LucideAngularModule et utilise
 *  <lucide-angular name="..."> dans son template — plus aucun glyphe emoji
 *  ou caractère unicode "icône" cassé. */
const LUCIDE_ICONS = {
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Bell,
  ChartColumn,
  Circle,
  CircleCheckBig,
  Compass,
  Eraser,
  FileText,
  Flag,
  FlaskConical,
  Hourglass,
  Key,
  LayoutDashboard,
  LogOut,
  Mail,
  Monitor,
  MessageCircle,
  Pause,
  PartyPopper,
  Pencil,
  Play,
  Plus,
  Radio,
  RefreshCw,
  Rocket,
  Save,
  Search,
  Send,
  Settings,
  Shuffle,
  Square,
  Timer,
  Trash2,
  TrendingUp,
  TriangleAlert,
  User,
  X,
  Zap
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])), // تفعيل الـ HttpClient للاتصال بالباكند + attache le token Keycloak
    importProvidersFrom(LucideAngularModule.pick(LUCIDE_ICONS)),
    // Blocks app bootstrap until Keycloak has redirected/authenticated the
    // user and a token is available — see KeycloakService's class doc for
    // why this is the whole login-redirect story on the frontend.
    provideAppInitializer(() => {
      const keycloakService = inject(KeycloakService);
      return keycloakService.init();
    })
  ]
};
