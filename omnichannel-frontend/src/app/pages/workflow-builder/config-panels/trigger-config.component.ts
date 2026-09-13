import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/**
 * TriggerConfigComponent. eventType is the one field that matters
 * functionally — it's mirrored up to the workflow's top-level
 * triggerEventType by WorkflowBuilderComponent, since that's the
 * denormalized column WorkflowTriggerConsumer actually filters ACTIVE
 * workflows on. There's exactly one TRIGGER node per workflow — the
 * palette doesn't enforce that, but WorkflowGraphValidator does at
 * activation time.
 */
@Component({
  selector: 'app-trigger-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './trigger-config.component.html'
})
export class TriggerConfigComponent {
  @Input() config: Record<string, any> = {};
  @Output() configChange = new EventEmitter<Record<string, any>>();

  get eventType(): string {
    return this.config['eventType'] ?? '';
  }

  set eventType(value: string) {
    this.configChange.emit({ ...this.config, eventType: value });
  }
}
