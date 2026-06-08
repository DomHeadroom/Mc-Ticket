import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent, MatPaginatorIntl } from '@angular/material/paginator';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
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
  imports: [
    DatePipe, FormsModule,
    MatTableModule, MatCardModule, MatPaginatorModule,
    MatInputModule, MatSelectModule, MatFormFieldModule, MatButtonModule,
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
  template: `
    <div class="container">
      <mat-card>
        <mat-card-content>
          <h2 class="list-title">Lista Richieste</h2>

          <div class="filter-bar">
            <div class="filter-bar-group">
              <mat-form-field appearance="outline" class="filter-search">
                <input matInput [(ngModel)]="filterSearch" placeholder="Cerca..." (keyup.enter)="onFilter()" />
              </mat-form-field>
              <button mat-stroked-button [color]="showAdvanced() ? 'primary' : ''" (click)="showAdvanced.set(!showAdvanced())">
                Ricerca Avanzata
              </button>
            </div>

            @if (showAdvanced()) {
              <div class="filter-advanced">
                <div class="filter-advanced-fields">
                  <mat-form-field appearance="outline" class="filter-select">
                    <mat-label>Stato</mat-label>
                    <mat-select [(ngModel)]="filterStatus">
                      <mat-option value="">Tutti</mat-option>
                      <mat-option value="open">Aperto</mat-option>
                      <mat-option value="in_progress">In corso</mat-option>
                      <mat-option value="pending_user">In attesa</mat-option>
                      <mat-option value="resolved">Risolto</mat-option>
                      <mat-option value="closed">Chiuso</mat-option>
                      <mat-option value="rejected">Rifiutato</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="filter-select">
                    <mat-label>Categoria</mat-label>
                    <mat-select [(ngModel)]="filterCategory">
                      <mat-option value="">Tutte</mat-option>
                      <mat-option value="rete">Rete</mat-option>
                      <mat-option value="database">Database</mat-option>
                      <mat-option value="bug-applicativo">Bug Applicativo</mat-option>
                      <mat-option value="configurazione">Configurazione</mat-option>
                      <mat-option value="hardware">Hardware</mat-option>
                      <mat-option value="servizi-web">Servizi Web</mat-option>
                      <mat-option value="altro">Altro</mat-option>
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="filter-select">
                    <mat-label>Priorità NLP</mat-label>
                    <mat-select [(ngModel)]="filterPriority">
                      <mat-option value="">Tutte</mat-option>
                      <mat-option value="p1">Critica</mat-option>
                      <mat-option value="p2">Alta</mat-option>
                      <mat-option value="p3">Media</mat-option>
                      <mat-option value="p4">Bassa</mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>

                <div class="filter-advanced-fields">
                  <mat-form-field appearance="outline" class="filter-date">
                    <mat-label>Dal</mat-label>
                    <input matInput type="date" [(ngModel)]="filterDateFrom" />
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="filter-date">
                    <mat-label>Al</mat-label>
                    <input matInput type="date" [(ngModel)]="filterDateTo" />
                  </mat-form-field>
                </div>

                <div class="filter-advanced-actions">
                  <button mat-button (click)="onReset()">Cancella</button>
                  <span class="filter-advanced-spacer"></span>
                  <button mat-flat-button color="primary" (click)="onFilter()">Filtra</button>
                </div>
              </div>
            }
          </div>

          <table mat-table [dataSource]="tickets()" multiTemplateDataRows class="ticket-table">
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Titolo</th>
              <td mat-cell *matCellDef="let t">
                <span class="expand-icon" [class.open]="expandedElement()?.id === t.id">&#9654;</span>
                {{ t.title }}
              </td>
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

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>Creato il</th>
              <td mat-cell *matCellDef="let t">{{ t.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
            </ng-container>

            <ng-container matColumnDef="expandedDetail">
              <td mat-cell *matCellDef="let t" [attr.colspan]="columns.length">
                @if (expandedElement()?.id === t.id) {
                  <div class="detail-content">
                    <p><strong>Descrizione:</strong> {{ t.description }}</p>
                    <p><strong>Richiedente:</strong> {{ t.requesterName ?? '-' }} &lt;{{ t.requesterEmail ?? '-' }}&gt;</p>
                    <p><strong>Categoria (utente):</strong> {{ categoryLabel(t.categoryUser) }}</p>
                    <p><strong>Categoria (NLP):</strong> {{ categoryLabel(t.categoryAuto) }}</p>
                    <p><strong>Priorità (utente):</strong> {{ priorityLabel(t.urgencyReported) }}</p>
                    <p><strong>Priorità (NLP):</strong> {{ priorityComputedLabel(t.priorityComputed) }}</p>
                    <p><strong>Keywords:</strong> {{ (t.keywords ?? []).join(', ') || '-' }}</p>
                    <p><strong>NLP:</strong> {{ t.nlpProcessed ? 'Sì' : 'No' }}</p>
                    <p><strong>File allegati:</strong> {{ (t.attachmentCount ?? 0) > 0 ? t.attachmentCount + ' file' : 'Nessun file' }}</p>
                    <p><strong>Creazione:</strong> {{ (t.openedAt | date:'dd/MM/yyyy HH:mm') || '-' }}</p>
                    <p><strong>Risoluzione:</strong> {{ (t.resolvedAt | date:'dd/MM/yyyy HH:mm') || '-' }}</p>
                    <p><strong>Chiusura:</strong> {{ (t.closedAt | date:'dd/MM/yyyy HH:mm') || '-' }}</p>
                  </div>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"
                class="clickable-row"
                [class.expanded]="expandedElement()?.id === row.id"
                (click)="toggleRow(row)"></tr>
            <tr mat-row *matRowDef="let row; columns: ['expandedDetail'];"
                class="detail-row"
                [class.visible]="expandedElement()?.id === row.id"></tr>
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
  protected readonly columns = ['title', 'status', 'urgency', 'createdAt'];
  protected readonly expandedElement = signal<TicketResponse | null>(null);
  protected readonly showAdvanced = signal(false);

  protected filterSearch = '';
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
      },
    });
  }

  protected onFilter(): void {
    this.page.set(0);
    this.loadTickets();
  }

  protected onReset(): void {
    this.filterSearch = '';
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
}
