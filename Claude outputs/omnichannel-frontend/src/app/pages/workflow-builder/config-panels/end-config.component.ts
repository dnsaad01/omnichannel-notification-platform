import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/** EndConfigComponent (architecture plan §7) — static, no configurable
 *  fields. EndNodeHandler always just completes the execution. */
@Component({
  selector: 'app-end-config',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-gray-950 border border-gray-800 rounded-lg p-4 text-sm text-gray-400">
      🏁 Marque la fin de l'exécution. Le workflow passe au statut <span class="text-emerald-400 font-medium">COMPLETED</span>.
      Aucune configuration nécessaire.
    </div>
  `
})
export class EndConfigComponent {
  @Input() config: Record<string, any> = {};
}
