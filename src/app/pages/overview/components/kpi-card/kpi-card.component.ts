import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition-all duration-200">
      <div class="flex items-center justify-between mb-3">
        <span class="text-xs font-medium text-slate-400 uppercase tracking-wider">{{ title }}</span>
        <div class="p-2 rounded-lg bg-slate-800/80 text-indigo-400">
          <lucide-icon [img]="icon" [size]="18"></lucide-icon>
        </div>
      </div>
      <div class="flex items-baseline justify-between">
        <h3 class="text-2xl font-bold text-white tracking-tight">{{ value }}</h3>
        <span [class]="isPositive ? 'text-emerald-400 bg-emerald-500/10' : 'text-rose-400 bg-rose-500/10'"
              class="text-xs font-semibold px-2 py-0.5 rounded-full">
          {{ trend }}
        </span>
      </div>
      <p class="text-[11px] text-slate-500 mt-2">{{ subtitle }}</p>
    </div>
  `
})
export class KpiCardComponent {
  @Input({ required: true }) title!: string;
  @Input({ required: true }) value!: string;
  @Input({ required: true }) trend!: string;
  @Input({ required: true }) isPositive: boolean = true;
  @Input({ required: true }) subtitle!: string;
  @Input({ required: true }) icon!: any;
}
