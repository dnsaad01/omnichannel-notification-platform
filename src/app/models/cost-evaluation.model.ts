export interface CostEvaluationResponse {
  recommendedChannel: string;
  estimatedCost: number;
  channelSuccessRate: number;
  rationale: string;
}
