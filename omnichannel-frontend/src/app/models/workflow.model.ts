/**
 * Mirrors the backend's workflow.dto classes exactly (ingestion-service,
 * com.eventflow.ingestion.workflow.dto) — see WorkflowResponse,
 * WorkflowExecutionResponse, WorkflowExecutionLogResponse.
 */

export type WorkflowStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED' | 'ARCHIVED';

export type ExecutionStatus = 'RUNNING' | 'WAITING' | 'ADVANCING' | 'COMPLETED' | 'FAILED';

export type LogLevel = 'INFO' | 'ERROR';

/**
 * Mirrors the backend's WorkflowRequest DTO exactly (ingestion-service,
 * com.eventflow.ingestion.workflow.dto.WorkflowRequest) — {name, description,
 * triggerEventType, definitionJson}. `definitionJson` is the JSON-stringified
 * {nodes, edges} graph blob (see workflow-draft.model.ts's
 * serializeDefinitionJson) — the backend has no separate nodes/edges fields,
 * so this is the one and only shape a create/update payload takes.
 */
export interface WorkflowRequest {
  name: string;
  description: string | null;
  triggerEventType: string;
  definitionJson: string;
}

export interface WorkflowResponse {
  id: number;
  name: string;
  description: string | null;
  status: WorkflowStatus;
  triggerEventType: string;
  version: number;
  parentWorkflowId: number | null;
  definitionJson: string;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowExecutionLogResponse {
  id: number;
  nodeId: string | null;
  nodeType: string | null;
  message: string;
  level: LogLevel;
  createdAt: string;
}

export interface WorkflowExecutionResponse {
  id: number;
  workflowId: number;
  workflowVersion: number;
  status: ExecutionStatus;
  currentNodeId: string | null;
  contextJson: string | null;
  nextWakeAt: string | null;
  startedAt: string;
  lastActivityAt: string;
  completedAt: string | null;
  /** Only populated by GET /api/workflow-executions/{id}, not the list endpoint. */
  logs: WorkflowExecutionLogResponse[] | null;
}

/** Shape of a Spring Data Page<T> as serialized to JSON — only the fields
 *  the Executions list page actually needs. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
