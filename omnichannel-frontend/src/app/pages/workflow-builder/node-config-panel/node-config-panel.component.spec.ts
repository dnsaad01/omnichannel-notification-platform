import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NodeConfigPanelComponent } from './node-config-panel.component';
import { DraftNode } from '../../../models/workflow-draft.model';

describe('NodeConfigPanelComponent', () => {
  let fixture: ComponentFixture<NodeConfigPanelComponent>;
  let component: NodeConfigPanelComponent;

  const triggerNode: DraftNode = { id: 'trigger-1', type: 'TRIGGER', name: 'Déclencheur', config: { eventType: '' }, position: { x: 0, y: 0 } };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NodeConfigPanelComponent] });
    fixture = TestBed.createComponent(NodeConfigPanelComponent);
    component = fixture.componentInstance;
  });

  it('should create with no node selected', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(component.node).toBeNull();
  });

  it('should render the matching config panel for the selected node type', () => {
    component.node = triggerNode;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-trigger-config')).toBeTruthy();
  });

  it('onNameInput should relay the new name via nameChange', () => {
    let emitted: string | undefined;
    component.nameChange.subscribe(n => (emitted = n));

    component.onNameInput('Nouveau nom');

    expect(emitted).toBe('Nouveau nom');
  });

  it('onConfigChange should relay the new config via configChange, untouched', () => {
    let emitted: any;
    component.configChange.subscribe(c => (emitted = c));
    const newConfig = { eventType: 'CART_ABANDONED' };

    component.onConfigChange(newConfig);

    expect(emitted).toBe(newConfig);
  });
});
