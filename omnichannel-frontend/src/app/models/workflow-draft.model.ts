/**
 * Builder-side graph model. Deliberately shaped to serialize 1:1 onto the
 * backend's definitionJson (com.eventflow.ingestion.workflow.engine.graph
 * .NodeDef / EdgeDef) — same field names, so parse/serialize here are
 * trivial JSON (de)serialization with no field mapping in between.
 */

export type WorkflowNodeType = 'TRIGGER' | 'NOTIFICATION' | 'WAIT' | 'GATEWAY' | 'END';

export interface DraftNode {
  id: string;
  type: WorkflowNodeType;
  name: string;
  config: Record<string, any>;
  position: { x: number; y: number };
}

export interface DraftEdge {
  id: string;
  source: string;
  /** Only meaningful when the source node is GATEWAY: 'yes' | 'no'. */
  sourceHandle: 'yes' | 'no' | null;
  target: string;
}

export interface WorkflowGraph {
  nodes: DraftNode[];
  edges: DraftEdge[];
}

export function emptyGraph(): WorkflowGraph {
  return { nodes: [], edges: [] };
}

/** A fresh workflow starts with one TRIGGER node so the canvas is never
 *  blank and there's an immediate, obvious first thing to configure. */
export function starterGraph(): WorkflowGraph {
  return {
    nodes: [
      { id: 'trigger-1', type: 'TRIGGER', name: 'Déclencheur', config: { eventType: '' }, position: { x: 60, y: 200 } }
    ],
    edges: []
  };
}

export function parseDefinitionJson(json: string | null | undefined): WorkflowGraph {
  if (!json) {
    return emptyGraph();
  }
  try {
    const parsed = JSON.parse(json);
    return {
      nodes: Array.isArray(parsed.nodes) ? parsed.nodes : [],
      edges: Array.isArray(parsed.edges) ? parsed.edges : []
    };
  } catch {
    return emptyGraph();
  }
}

export function serializeDefinitionJson(graph: WorkflowGraph): string {
  return JSON.stringify(graph);
}

let idCounter = 0;

export function createNodeId(type: WorkflowNodeType): string {
  idCounter += 1;
  return `${type.toLowerCase()}-${Date.now().toString(36)}-${idCounter}`;
}

export function createEdgeId(): string {
  idCounter += 1;
  return `edge-${Date.now().toString(36)}-${idCounter}`;
}

export function defaultNameFor(type: WorkflowNodeType): string {
  switch (type) {
    case 'TRIGGER': return 'Déclencheur';
    case 'NOTIFICATION': return 'Notification';
    case 'WAIT': return 'Attente';
    case 'GATEWAY': return 'Condition';
    case 'END': return 'Fin';
  }
}

export function defaultConfigFor(type: WorkflowNodeType): Record<string, any> {
  switch (type) {
    case 'TRIGGER': return { eventType: '' };
    case 'NOTIFICATION': return { templateId: null, channel: '', recipientPath: '' };
    case 'WAIT': return { duration: 1, unit: 'HOURS' };
    case 'GATEWAY': return { variable: '', operator: 'equals', value: '' };
    case 'END': return {};
  }
}
