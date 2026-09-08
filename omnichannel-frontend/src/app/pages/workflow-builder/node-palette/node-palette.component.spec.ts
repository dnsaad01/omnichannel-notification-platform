import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NodePaletteComponent } from './node-palette.component';

describe('NodePaletteComponent', () => {
  let fixture: ComponentFixture<NodePaletteComponent>;
  let component: NodePaletteComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NodePaletteComponent] });
    fixture = TestBed.createComponent(NodePaletteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should group the 5 node types into Déclencheurs / Actions / Contrôle', () => {
    expect(component.groups.map(g => g.title)).toEqual(['Déclencheurs', 'Actions', 'Contrôle']);
    expect(component.groups[0].items.map(i => i.type)).toEqual(['TRIGGER']);
    expect(component.groups[1].items.map(i => i.type)).toEqual(['NOTIFICATION']);
    expect(component.groups[2].items.map(i => i.type)).toEqual(['WAIT', 'GATEWAY', 'END']);
  });

  it('onDragStart should set the dataTransfer payload, effectAllowed, and emit the dragged type', () => {
    // A plain mock rather than a real DataTransfer: outside an actual
    // browser-initiated drag gesture, a real DataTransfer silently refuses
    // setData()/effectAllowed writes (a browser security restriction),
    // which would make this test pass or fail for the wrong reason.
    const store = new Map<string, string>();
    const dataTransfer: any = {
      effectAllowed: '',
      setData: (key: string, value: string) => store.set(key, value),
      getData: (key: string) => store.get(key) ?? ''
    };
    const event = { dataTransfer } as unknown as DragEvent;
    let emitted: string | undefined;
    component.nodeTypeDragStart.subscribe(t => (emitted = t));

    component.onDragStart(event, 'WAIT');

    expect(dataTransfer.getData('application/x-workflow-node-type')).toBe('WAIT');
    expect(dataTransfer.effectAllowed).toBe('copy');
    expect(emitted).toBe('WAIT');
  });

  it('onDragStart should not throw when the event has no dataTransfer', () => {
    const event = { dataTransfer: null } as unknown as DragEvent;
    expect(() => component.onDragStart(event, 'END')).not.toThrow();
  });
});
