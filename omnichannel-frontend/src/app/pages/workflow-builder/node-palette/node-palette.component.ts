import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { WorkflowNodeType } from '../../../models/workflow-draft.model';

interface PaletteItem {
  type: WorkflowNodeType;
  icon: string;
  label: string;
}

interface PaletteGroup {
  title: string;
  items: PaletteItem[];
}

/**
 * Left sidebar: draggable palette (NodePaletteComponent) grouped as
 * DÉCLENCHEURS / ACTIONS / CONTRÔLE. Uses plain HTML5 drag-and-drop
 * (dataTransfer) rather than any ngx-vflow-specific API, so the palette
 * has zero dependency on the canvas library's internals —
 * WorkflowCanvasComponent just needs to handle a standard `drop` event.
 */
@Component({
  selector: 'app-node-palette',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './node-palette.component.html'
})
export class NodePaletteComponent {
  @Output() nodeTypeDragStart = new EventEmitter<WorkflowNodeType>();

  readonly groups: PaletteGroup[] = [
    { title: 'Déclencheurs', items: [{ type: 'TRIGGER', icon: 'zap', label: 'Trigger' }] },
    { title: 'Actions', items: [{ type: 'NOTIFICATION', icon: 'mail', label: 'Notification' }] },
    {
      title: 'Contrôle',
      items: [
        { type: 'WAIT', icon: 'timer', label: 'Wait' },
        { type: 'GATEWAY', icon: 'shuffle', label: 'Gateway' },
        { type: 'END', icon: 'flag', label: 'End' }
      ]
    }
  ];

  onDragStart(event: DragEvent, type: WorkflowNodeType) {
    event.dataTransfer?.setData('application/x-workflow-node-type', type);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'copy';
    }
    this.nodeTypeDragStart.emit(type);
  }
}
