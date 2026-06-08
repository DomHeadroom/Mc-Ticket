import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent, MatPaginatorIntl } from '@angular/material/paginator';
import { TicketResponse, BASE_PATH } from '../../generated';

interface PageResponse {
  content: TicketResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-ticket-list',
  imports: [DatePipe, MatTableModule, MatCardModule, MatPaginatorModule],
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
  template: `
    <div class="container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Lista Richieste</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="tickets()" class="ticket-table">
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Titolo</th>
              <td mat-cell *matCellDef="let t">{{ t.title }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Stato</th>
              <td mat-cell *matCellDef="let t">
                <span class="status-badge status-{{ t.status }}">{{ statusLabel(t.status) }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="urgency">
              <th mat-header-cell *matHeaderCellDef>Priorità</th>
              <td mat-cell *matCellDef="let t">{{ priorityLabel(t.urgencyReported) }}</td>
            </ng-container>

            <ng-container matColumnDef="categoryAuto">
              <th mat-header-cell *matHeaderCellDef>Categoria NLP</th>
              <td mat-cell *matCellDef="let t">{{ categoryLabel(t.categoryAuto) }}</td>
            </ng-container>

            <ng-container matColumnDef="keywords">
              <th mat-header-cell *matHeaderCellDef>Keywords</th>
              <td mat-cell *matCellDef="let t">{{ (t.keywords ?? []).slice(0, 3).join(', ') }}</td>
            </ng-container>

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>Creato il</th>
              <td mat-cell *matCellDef="let t">{{ t.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>

          @if (tickets().length === 0) {
            <p class="empty">Nessun ticket trovato.</p>
          }

          @if (totalPages() > 1) {
            <mat-paginator
              [length]="totalElements()"
              [pageSize]="pageSize()"
              [pageIndex]="page()"
              (page)="onPage($event)"
              showFirstLastButtons
            ></mat-paginator>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styleUrl: 'ticket-list.css',
})
export class TicketList implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly basePath = inject(BASE_PATH);

  protected readonly tickets = signal<TicketResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly columns = ['title', 'status', 'urgency', 'categoryAuto', 'keywords', 'createdAt'];

  ngOnInit() {
    this.loadTickets();
  }

  private loadTickets(): void {
    const params = new HttpParams()
      .set('page', this.page())
      .set('size', this.pageSize());

    this.http.get<PageResponse>(`${this.basePath}/api/tickets`, { params }).subscribe({
      next: (res) => {
        this.tickets.set(res.content);
        this.totalElements.set(res.totalElements);
        this.totalPages.set(res.totalPages);
      },
    });
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
}
