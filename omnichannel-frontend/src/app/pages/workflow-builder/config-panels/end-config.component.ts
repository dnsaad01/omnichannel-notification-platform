import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';

/** EndConfigComponent — static, no configurable
 *  fields. EndNodeHandler always just completes the execution. */
@Component({
  selector: 'app-end-config',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="bg-gray-950 border border-gray-800 rounded-lg p-4 text-sm text-gray-400 flex items-start gap-2">
      <lucide-angular name="flag" [size]="16" class="shrink-0 mt-0.5"></lucide-angular>
      <span>Marque la fin de l'exécution. Le workflow passe au statut <span class="text-emerald-400 font-medium">COMPLETED</span>.
      Aucune configuration nécessaire.</span>
    </div>
  `
})
export class EndConfigComponent {
  @Input() config: Record<string, any> = {};
}
