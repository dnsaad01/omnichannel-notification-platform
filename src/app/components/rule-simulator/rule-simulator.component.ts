import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RuleSimulatorService } from '../../services/rule-simulator.service';
import { CostEvaluationResponse } from '../../models/cost-evaluation.model';

@Component({
  selector: 'app-rule-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-simulator.component.html',
  styleUrl: './rule-simulator.component.scss'
})
export class RuleSimulatorComponent {
  recipientId: string = 'usr_1001';
  priority: string = 'NORMAL';
  payloadSizeKb: number = 2.4;

  isLoading: boolean = false;
  simulationResult: CostEvaluationResponse | null = null;
  errorMessage: string = '';

  constructor(private simulatorService: RuleSimulatorService) {}

  runSimulation(): void {
    if (!this.recipientId.trim()) return;

    this.isLoading = true;
    this.errorMessage = '';
    this.simulationResult = null;

    this.simulatorService.simulateRoute(this.recipientId, this.priority).subscribe({
      next: (res) => {
        this.simulationResult = res;
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = 'Failed to evaluate route simulation from backend engine.';
        this.isLoading = false;
      }
    });
  }
}
