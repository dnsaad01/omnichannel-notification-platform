import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/** WaitConfigComponent (architecture plan §7) — matches WaitNodeHandler's
 *  config shape exactly: { duration: number, unit: ChronoUnit name }. */
@Component({
  selector: 'app-wait-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './wait-config.component.html'
})
export class WaitConfigComponent {
  @Input() config: Record<string, any> = {};
  @Output() configChange = new EventEmitter<Record<string, any>>();

  readonly units = ['MINUTES', 'HOURS', 'DAYS'];

  get duration(): number {
    return this.config['duration'] ?? 1;
  }

  set duration(value: number) {
    this.configChange.emit({ ...this.config, duration: Number(value) });
  }

  get unit(): string {
    return this.config['unit'] ?? 'HOURS';
  }

  set unit(value: string) {
    this.configChange.emit({ ...this.config, unit: value });
  }
}
