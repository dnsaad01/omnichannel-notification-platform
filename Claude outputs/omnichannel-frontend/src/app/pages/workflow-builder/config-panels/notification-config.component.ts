import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface TemplateOption {
  id: number;
  name: string;
  channel: string;
}

/**
 * NotificationConfigComponent (architecture plan §7). templateId is a real
 * NotificationTemplate id from Phase 0's /api/templates — the dropdown is
 * populated by WorkflowBuilderComponent (fetched once, not per node
 * selection) rather than this component calling TemplateService itself.
 * channel/recipientPath are optional overrides; NotificationNodeHandler
 * (Phase 1) falls back to the template's own channel and to
 * recipientId/userId/email/phone in the execution context when left blank.
 */
@Component({
  selector: 'app-notification-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notification-config.component.html'
})
export class NotificationConfigComponent {
  @Input() config: Record<string, any> = {};
  @Input() templates: TemplateOption[] = [];
  @Output() configChange = new EventEmitter<Record<string, any>>();

  get templateId(): number | null {
    return this.config['templateId'] ?? null;
  }

  set templateId(value: number | string | null) {
    const numeric = value === null || value === '' ? null : Number(value);
    this.configChange.emit({ ...this.config, templateId: numeric });
  }

  get channel(): string {
    return this.config['channel'] ?? '';
  }

  set channel(value: string) {
    this.configChange.emit({ ...this.config, channel: value });
  }

  get recipientPath(): string {
    return this.config['recipientPath'] ?? '';
  }

  set recipientPath(value: string) {
    this.configChange.emit({ ...this.config, recipientPath: value });
  }

  get selectedTemplate(): TemplateOption | undefined {
    return this.templates.find(t => t.id === this.templateId);
  }
}
