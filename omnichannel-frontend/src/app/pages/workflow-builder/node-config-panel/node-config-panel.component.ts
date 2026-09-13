import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { DraftNode } from '../../../models/workflow-draft.model';
import { TriggerConfigComponent } from '../config-panels/trigger-config.component';
import { NotificationConfigComponent, TemplateOption } from '../config-panels/notification-config.component';
import { WaitConfigComponent } from '../config-panels/wait-config.component';
import { GatewayConfigComponent } from '../config-panels/gateway-config.component';
import { EndConfigComponent } from '../config-panels/end-config.component';

/**
 * Right sidebar (NodeConfigPanelComponent): common controls (name, delete)
 * plus one of the five per-type config components, switched on the
 * selected node's type. All config components write into the same plain
 * `config` object shape the backend's NodeDef.config expects — this
 * wrapper just relays configChange upward untouched.
 */
@Component({
  selector: 'app-node-config-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideAngularModule,
    TriggerConfigComponent,
    NotificationConfigComponent,
    WaitConfigComponent,
    GatewayConfigComponent,
    EndConfigComponent
  ],
  templateUrl: './node-config-panel.component.html'
})
export class NodeConfigPanelComponent {
  @Input() node: DraftNode | null = null;
  @Input() templates: TemplateOption[] = [];
  @Input() canDelete = true;

  @Output() nameChange = new EventEmitter<string>();
  @Output() configChange = new EventEmitter<Record<string, any>>();
  @Output() deleteNode = new EventEmitter<void>();

  onNameInput(value: string) {
    this.nameChange.emit(value);
  }

  onConfigChange(config: Record<string, any>) {
    this.configChange.emit(config);
  }
}
