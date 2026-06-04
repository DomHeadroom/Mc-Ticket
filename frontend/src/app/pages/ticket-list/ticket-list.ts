import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { TicketControllerService, TicketResponse } from '../../generated';

@Component({
  selector: 'app-ticket-list',
  imports: [DatePipe, MatTableModule, MatCardModule],
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
              <td mat-cell *matCellDef="let t">{{ t.urgencyReported }}</td>
            </ng-container>

            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>Creato il</th>
              <td mat-cell *matCellDef="let t">{{ t.createdAt | date:'short' }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"></tr>
          </table>

          @if (tickets().length === 0) {
            <p class="empty">Nessun ticket trovato.</p>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container {
      max-width: 1200px;
      margin: 32px auto;
      padding: 0 16px;
    }
    .ticket-table {
      width: 100%;
    }
    .empty {
      text-align: center;
      padding: 32px;
      color: rgba(0,0,0,0.5);
    }
    .status-badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
    }
    .status-open { background: #e3f2fd; color: #1565c0; }
    .status-in_progress { background: #fff3e0; color: #e65100; }
    .status-pending_user { background: #f3e5f5; color: #7b1fa2; }
    .status-resolved { background: #e8f5e9; color: #2e7d32; }
    .status-closed { background: #eceff1; color: #546e7a; }
    .status-rejected { background: #ffebee; color: #c62828; }
  `],
})
export class TicketList implements OnInit {
  private readonly ticketApi = inject(TicketControllerService);

  protected readonly tickets = signal<TicketResponse[]>([]);
  protected readonly columns = ['title', 'status', 'urgency', 'createdAt'];

  ngOnInit() {
    this.ticketApi.getAllTickets().subscribe({
      next: (res) => this.tickets.set(res),
    });
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
