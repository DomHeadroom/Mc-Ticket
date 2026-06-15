import { Component, inject, OnInit, signal, ChangeDetectorRef } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent, MatPaginatorIntl } from '@angular/material/paginator';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';
import { TicketResponse, AttachmentResponse, BASE_PATH } from '../../generated';

interface PageResponse {
  content: TicketResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-ticket-list',
  imports: [
    DatePipe, FormsModule,
    MatTableModule, MatCardModule, MatPaginatorModule,
    MatInputModule, MatSelectModule, MatFormFieldModule, MatButtonModule, MatIconModule,
  ],
  providers: [
    {
      provide: MatPaginatorIntl,
      useValue: {
        itemsPerPageLabel: 'Elementi per pagina',
        nextPageLabel: 'Prossima',
        previousPageLabel: 'Precedente',
        firstPageLabel: 'Prima pagina',
        lastPageLabel: 'Ultima pagina',
        getRangeLabel: (page: number, size: number, length: number) => {
          if (length === 0) return '0 di 0';
          const start = page * size + 1;
          const end = Math.min((page + 1) * size, length);
          return `${start} - ${end} di ${length}`;
        },
      } as MatPaginatorIntl,
    },
  ],
  templateUrl: 'ticket-list.html',
  styleUrl: 'ticket-list.css',
})
export class TicketList implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly tickets = signal<TicketResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly columns = ['title', 'status', 'urgency', 'createdAt'];
  protected readonly expandedElement = signal<TicketResponse | null>(null);
  protected readonly showAdvanced = signal(false);

  protected pendingStatus: Record<string, string> = {};

  protected filterSearch = '';
  protected filterKeyword = '';
  protected filterStatus = '';
  protected filterCategory = '';
  protected filterPriority = '';
  protected filterDateFrom = '';
  protected filterDateTo = '';

  ngOnInit() {
    this.loadTickets();
  }

  private loadTickets(): void {
    let params = new HttpParams()
      .set('page', this.page())
      .set('size', this.pageSize());

    if (this.filterSearch) params = params.set('search', this.filterSearch);
    if (this.filterKeyword) params = params.set('keyword', this.filterKeyword);
    if (this.filterStatus) params = params.set('status', this.filterStatus);
    if (this.filterCategory) params = params.set('categorySlug', this.filterCategory);
    if (this.filterPriority) params = params.set('priority', this.filterPriority);
    if (this.filterDateFrom) params = params.set('dateFrom', this.filterDateFrom);
    if (this.filterDateTo) params = params.set('dateTo', this.filterDateTo);

    this.http.get<PageResponse>(`${this.basePath}/api/tickets`, { params }).subscribe({
      next: (res) => {
        this.tickets.set(res.content);
        this.totalElements.set(res.totalElements);
        this.totalPages.set(res.totalPages);
        this.page.set(res.number);
        setTimeout(() => this.cdr.detectChanges());
      },
    });
  }

  protected onFilter(): void {
    this.page.set(0);
    this.loadTickets();
  }

  protected onReset(): void {
    this.filterSearch = '';
    this.filterKeyword = '';
    this.filterStatus = '';
    this.filterCategory = '';
    this.filterPriority = '';
    this.filterDateFrom = '';
    this.filterDateTo = '';
    this.page.set(0);
    this.loadTickets();
  }

  protected toggleRow(row: TicketResponse): void {
    this.expandedElement.update(current =>
      current?.id === row.id ? null : row,
    );
  }

  protected onPage(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.pageSize.set(e.pageSize);
    this.loadTickets();
  }

  protected priorityLabel(priority: string | undefined): string {
    const labels: Record<string, string> = {
      low: 'Bassa',
      medium: 'Media',
      high: 'Alta',
      critical: 'Critica',
    };
    return labels[priority ?? ''] ?? priority ?? '';
  }

  protected priorityComputedLabel(priority: string | undefined): string {
    const labels: Record<string, string> = {
      p1: 'Critica',
      p2: 'Alta',
      p3: 'Media',
      p4: 'Bassa',
    };
    return labels[priority ?? ''] ?? priority ?? '-';
  }

  protected categoryLabel(slug: string | undefined): string {
    if (!slug) return '-';
    const labels: Record<string, string> = {
      rete: 'Rete',
      database: 'Database',
      'bug-applicativo': 'Bug Applicativo',
      configurazione: 'Configurazione',
      hardware: 'Hardware',
      'servizi-web': 'Servizi Web',
      altro: 'Altro',
    };
    return labels[slug] ?? slug;
  }

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      open: 'Aperto',
      in_progress: 'In corso',
      pending_user: 'In attesa',
      resolved: 'Risolto',
      closed: 'Chiuso',
      rejected: 'Rifiutato',
    };
    return labels[status] ?? status;
  }

  protected onUpdateStatus(ticket: TicketResponse): void {
    const newStatus = this.pendingStatus[ticket.id!];
    if (!newStatus || newStatus === ticket.status) return;

    this.http.patch<TicketResponse>(`${this.basePath}/api/tickets/${ticket.id}/status`, { status: newStatus })
      .subscribe({
        next: (updated) => {
          this.tickets.update(list => list.map(t => t.id === updated.id ? updated : t));
          delete this.pendingStatus[ticket.id!];
          setTimeout(() => this.cdr.detectChanges());
        },
      });
  }

  protected attachmentUrl(a: AttachmentResponse): string {
    return `/api/attachments/${a.id}`;
  }
}
