import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-engagement-optimizer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './engagement-optimizer.component.html',
  styleUrl: './engagement-optimizer.component.scss'
})
export class EngagementOptimizerComponent {
  // Top Metrics
  totalCost: string = '$1,420.50';
  costSavings: string = '-$340.00 saved via Smart Routing';
  openRatePct: number = 68.4;
  optimalChannelRatio: string = '72:28 Push vs SMS';
  bounceRatePct: number = 0.41;

  // Sandbox Mode Switcher Toggle
  isSandboxMode: boolean = false;

  // Sandbox Test Simulation State
  testUserId: string = 'usr_test_99';
  testChannel: string = 'PUSH';
  simulating: boolean = false;
  simulationStep: number = 0;
  simulationLog: string[] = [];

  toggleMode(): void {
    this.isSandboxMode = !this.isSandboxMode;
    this.simulationStep = 0;
    this.simulationLog = [];
  }

  runSimulation(): void {
    this.simulating = true;
    this.simulationStep = 1;
    this.simulationLog = ['[Step 1] Event Received: Dispatching Push Notification to ' + this.testUserId];

    setTimeout(() => {
      this.simulationStep = 2;
      this.simulationLog.push('[Step 2] Push Notification sent ($0.001). Monitoring engagement window...');
    }, 1200);

    setTimeout(() => {
      this.simulationStep = 3;
      this.simulationLog.push('[Step 3] 3-Hour Window Expired: Notification unopened.');
    }, 2400);

    setTimeout(() => {
      this.simulationStep = 4;
      this.simulationLog.push('[Step 4] Triggering Smart Fallback: SMS sent to recipient ($0.015).');
      this.simulating = false;
    }, 3600);
  }
}
