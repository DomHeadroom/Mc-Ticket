import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';

import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TicketControllerService, CategoryResponse } from '../../generated';

@Component({
  selector: 'app-home',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,

    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
  ],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly ticketApi = inject(TicketControllerService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly submitting = signal(false);
  protected readonly bulkSubmitting = signal(false);
  protected selectedFile = signal<File | null>(null);
  protected selectedBulkFile = signal<File | null>(null);

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(500)]],
    description: ['', Validators.required],
    categorySlug: [''],
    urgencyReported: ['medium'],
  });

  ngOnInit() {
    this.ticketApi.getActiveCategories().subscribe({
      next: (cats) => this.categories.set(cats),
    });
  }

  protected onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile.set(input.files[0]);
    }
  }

  protected onReset() {
    this.form.reset({ title: '', description: '', categorySlug: '', urgencyReported: 'medium' });
    this.selectedFile.set(null);
  }

  protected onBulkFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedBulkFile.set(input.files[0]);
    }
  }

  protected onSubmit() {
    if (this.form.invalid) return;
    this.submitting.set(true);

    const raw = this.form.getRawValue();
    this.ticketApi.createTicket({
      title: raw.title!,
      description: raw.description!,
      categorySlug: raw.categorySlug || undefined,
      urgencyReported: raw.urgencyReported as 'low' | 'medium' | 'high' | 'critical' | undefined,
    }).subscribe({
      next: () => {
        this.snackBar.open('Ticket creato con successo', 'Chiudi', { duration: 3000 });
        this.form.reset({ title: '', description: '', categorySlug: '', urgencyReported: 'medium' });
        this.selectedFile.set(null);
        this.submitting.set(false);
      },
      error: () => {
        this.snackBar.open('Errore durante la creazione del ticket', 'Chiudi', { duration: 5000 });
        this.submitting.set(false);
      },
    });
  }

  protected onSubmitBulk() {
    const file = this.selectedBulkFile();
    if (!file) return;
    this.bulkSubmitting.set(true);

    this.ticketApi.bulkImport(file).subscribe({
      next: (res) => {
        this.snackBar.open(
          `Importazione completata: ${res.processedRows} processati, ${res.failedRows} falliti su ${res.totalRows} totali`,
          'Chiudi',
          { duration: 5000 }
        );
        this.selectedBulkFile.set(null);
        this.bulkSubmitting.set(false);
      },
      error: () => {
        this.snackBar.open('Errore durante l\'importazione bulk', 'Chiudi', { duration: 5000 });
        this.bulkSubmitting.set(false);
      },
    });
  }
}
