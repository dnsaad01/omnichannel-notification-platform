import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/** GatewayConfigComponent (architecture plan §7) — matches
 *  GatewayNodeHandler's config shape exactly: { variable, operator, value }.
 *  `value` is hidden for the `exists` operator, which ignores it. */
@Component({
  selector: 'app-gateway-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gateway-config.component.html'
})
export class GatewayConfigComponent {
  @Input() config: Record<string, any> = {};
  @Output() configChange = new EventEmitter<Record<string, any>>();

  readonly operators = [
    { value: 'equals', label: '= égal à' },
    { value: 'notEquals', label: '≠ différent de' },
    { value: 'greaterThan', label: '> supérieur à' },
    { value: 'lessThan', label: '< inférieur à' },
    { value: 'exists', label: 'existe' }
  ];

  get variable(): string {
    return this.config['variable'] ?? '';
  }

  set variable(value: string) {
    this.configChange.emit({ ...this.config, variable: value });
  }

  get operator(): string {
    return this.config['operator'] ?? 'equals';
  }

  set operator(value: string) {
    this.configChange.emit({ ...this.config, operator: value });
  }

  get value(): string {
    return this.config['value'] ?? '';
  }

  set value(value: string) {
    this.configChange.emit({ ...this.config, value });
  }
}
