import {
  createEdgeId,
  createNodeId,
  defaultConfigFor,
  defaultNameFor,
  emptyGraph,
  parseDefinitionJson,
  serializeDefinitionJson,
  starterGraph,
  WorkflowNodeType
} from './workflow-draft.model';

/**
 * Pure, dependency-free functions — the builder-side graph model that
 * serializes 1:1 onto the backend's definitionJson (see this file's own
 * class doc comment). No TestBed needed at all; these are exercised exactly
 * like WorkflowBuilderComponent itself exercises them.
 */
describe('workflow-draft.model', () => {
  describe('emptyGraph', () => {
    it('should return an empty nodes/edges graph', () => {
      expect(emptyGraph()).toEqual({ nodes: [], edges: [] });
    });

    it('should return a fresh object on every call rather than a shared reference', () => {
      expect(emptyGraph()).not.toBe(emptyGraph());
    });
  });

  describe('starterGraph', () => {
    it('should start with exactly one TRIGGER node and no edges', () => {
      const graph = starterGraph();
      expect(graph.nodes.length).toBe(1);
      expect(graph.nodes[0].type).toBe('TRIGGER');
      expect(graph.nodes[0].config).toEqual({ eventType: '' });
      expect(graph.edges).toEqual([]);
    });
  });

  describe('parseDefinitionJson', () => {
    it('should return an empty graph for null/undefined/empty input', () => {
      expect(parseDefinitionJson(null)).toEqual({ nodes: [], edges: [] });
      expect(parseDefinitionJson(undefined)).toEqual({ nodes: [], edges: [] });
      expect(parseDefinitionJson('')).toEqual({ nodes: [], edges: [] });
    });

    it('should parse a valid graph JSON string', () => {
      const json = JSON.stringify({
        nodes: [{ id: 'n1', type: 'TRIGGER', name: 'Déclencheur', config: {}, position: { x: 0, y: 0 } }],
        edges: [{ id: 'e1', source: 'n1', sourceHandle: null, target: 'n2' }]
      });

      const graph = parseDefinitionJson(json);

      expect(graph.nodes.length).toBe(1);
      expect(graph.edges.length).toBe(1);
    });

    it('should return an empty graph for malformed JSON rather than throwing', () => {
      expect(() => parseDefinitionJson('{not valid')).not.toThrow();
      expect(parseDefinitionJson('{not valid')).toEqual({ nodes: [], edges: [] });
    });

    it('should default nodes/edges to an empty array when the parsed JSON is missing or has non-array fields', () => {
      expect(parseDefinitionJson('{}')).toEqual({ nodes: [], edges: [] });
      expect(parseDefinitionJson('{"nodes":"not-an-array","edges":123}')).toEqual({ nodes: [], edges: [] });
    });
  });

  describe('serializeDefinitionJson', () => {
    it('should JSON-stringify the graph as-is', () => {
      const graph = { nodes: [{ id: 'n1', type: 'TRIGGER' as WorkflowNodeType, name: 'x', config: {}, position: { x: 0, y: 0 } }], edges: [] };
      expect(serializeDefinitionJson(graph)).toBe(JSON.stringify(graph));
    });

    it('should round-trip through parseDefinitionJson unchanged', () => {
      const graph = starterGraph();
      expect(parseDefinitionJson(serializeDefinitionJson(graph))).toEqual(graph);
    });
  });

  describe('createNodeId / createEdgeId', () => {
    it('should prefix a node id with the lowercased node type', () => {
      expect(createNodeId('GATEWAY')).toMatch(/^gateway-/);
      expect(createNodeId('WAIT')).toMatch(/^wait-/);
    });

    it('should never return the same node id twice, even for the same type back-to-back', () => {
      const ids = new Set(Array.from({ length: 20 }, () => createNodeId('NOTIFICATION')));
      expect(ids.size).toBe(20);
    });

    it('should prefix every edge id with "edge-" and never repeat', () => {
      const ids = new Set(Array.from({ length: 20 }, () => createEdgeId()));
      expect(ids.size).toBe(20);
      ids.forEach(id => expect(id).toMatch(/^edge-/));
    });
  });

  describe('defaultNameFor', () => {
    it('should return the expected French label for every node type', () => {
      expect(defaultNameFor('TRIGGER')).toBe('Déclencheur');
      expect(defaultNameFor('NOTIFICATION')).toBe('Notification');
      expect(defaultNameFor('WAIT')).toBe('Attente');
      expect(defaultNameFor('GATEWAY')).toBe('Condition');
      expect(defaultNameFor('END')).toBe('Fin');
    });
  });

  describe('defaultConfigFor', () => {
    it('should return the exact config shape each node handler expects, per type', () => {
      expect(defaultConfigFor('TRIGGER')).toEqual({ eventType: '' });
      expect(defaultConfigFor('NOTIFICATION')).toEqual({ templateId: null, channel: '', recipientPath: '' });
      expect(defaultConfigFor('WAIT')).toEqual({ duration: 1, unit: 'HOURS' });
      expect(defaultConfigFor('GATEWAY')).toEqual({ variable: '', operator: 'equals', value: '' });
      expect(defaultConfigFor('END')).toEqual({});
    });
  });
});
