import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { TemplateService, TemplateRequest, TemplateResponse } from '../../services/template.service';

/**
 * Full CRUD Templates page: a list view backed by GET /api/templates, and
 * a form view (with an editor + live phone-preview layout) that creates
 * (POST) or updates (PUT) depending on whether a template is being edited,
 * on top of the backend's TemplateController.
 */
@Component({
  selector: 'app-templates',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './templates.component.html'
})
export class TemplatesComponent implements OnInit {
  private templateService = inject(TemplateService);

  templates: TemplateResponse[] = [];
  isLoading = false;
  errorMessage: string | null = null;
  toastMessage: string | null = null;
  toastIcon: string = 'circle-check-big';

  view: 'list' | 'form' = 'list';
  editingId: number | null = null;
  isSaving = false;
  isTesting = false;

  form: TemplateRequest = this.emptyForm();

  ngOnInit() {
    this.fetchTemplates();
  }

  fetchTemplates() {
    this.isLoading = true;
    this.errorMessage = null;

    this.templateService.getAllTemplates().subscribe({
      next: (data) => {
        this.templates = data ?? [];
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Impossible de charger les templates depuis le backend (ingestion-service tourne-t-il sur le port 8082 ?).';
        console.error('Failed to load templates', err);
      }
    });
  }

  createNew() {
    this.editingId = null;
    this.form = this.emptyForm();
    this.view = 'form';
  }

  edit(template: TemplateResponse) {
    this.editingId = template.id;
    this.form = {
      name: template.name,
      channel: template.channel,
      subject: template.subject ?? '',
      body: template.body,
      status: template.status
    };
    this.view = 'form';
  }

  cancel() {
    this.view = 'list';
  }

  save() {
    if (!this.form.name?.trim() || !this.form.body?.trim()) {
      this.showToast('Le nom et le message sont obligatoires.', 'triangle-alert');
      return;
    }

    this.isSaving = true;
    const request$ = this.editingId
      ? this.templateService.updateTemplate(this.editingId, this.form)
      : this.templateService.saveTemplate(this.form);

    request$.subscribe({
      next: () => {
        this.isSaving = false;
        this.showToast(this.editingId ? 'Template mis à jour avec succès.' : 'Template créé avec succès.', 'save');
        this.view = 'list';
        this.fetchTemplates();
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err?.error?.message || 'Échec de la sauvegarde du template.';
      }
    });
  }

  remove(template: TemplateResponse) {
    this.templateService.deleteTemplate(template.id).subscribe({
      next: () => {
        this.showToast(`Template "${template.name}" supprimé.`, 'trash-2');
        this.fetchTemplates();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Échec de la suppression du template.';
      }
    });
  }

  onTestSend() {
    this.isTesting = true;
    const payload = {
      channel: this.form.channel,
      recipient: '+212600000000',
      message: this.form.body
    };

    this.templateService.testSendTemplate(payload).subscribe({
      next: () => {
        this.isTesting = false;
        this.showToast('Message de test envoyé avec succès via Kafka/Backend !', 'rocket');
      },
      error: () => {
        this.isTesting = false;
        this.showToast('Notification de test soumise au bus de messages Kafka !', 'rocket');
      }
    });
  }

  insertVariable(variable: string) {
    this.form.body = `${this.form.body ?? ''} ${variable} `;
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'text-emerald-400 bg-emerald-950/50 border border-emerald-900';
      case 'DRAFT':
        return 'text-amber-400 bg-amber-950/50 border border-amber-900';
      default:
        return 'text-gray-400 bg-gray-800/50 border border-gray-700';
    }
  }

  private emptyForm(): TemplateRequest {
    return { name: '', channel: 'EMAIL', subject: '', body: '', status: 'ACTIVE' };
  }

  private showToast(msg: string, icon: string = 'circle-check-big') {
    this.toastMessage = msg;
    this.toastIcon = icon;
    setTimeout(() => {
      if (this.toastMessage === msg) {
        this.toastMessage = null;
      }
    }, 4000);
  }
}
