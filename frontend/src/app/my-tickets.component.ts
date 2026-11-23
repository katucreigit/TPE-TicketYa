import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface Entrada {
  entradaId: string;
  eventoId: number;
  sectorId: number;
  estado: string;
  precioUnitario: number;
  fila?: string;
  numero?: number;
  codigoQr?: string;
}

@Component({
  selector: 'app-my-tickets',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page">
      <h3 class="mb-4">Mis Entradas</h3>

      @if (loading) {
        <div class="text-center py-5"><div class="spinner-border text-primary"></div></div>
      } @else if (entradas.length === 0) {
        <div class="text-center py-5 text-muted">
          <p>No tenés entradas todavía.</p>
          <a routerLink="/events" class="btn btn-primary btn-sm">Ver eventos</a>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle">
            <thead class="table-light">
              <tr>
                <th>Evento</th>
                <th>Sector</th>
                <th>Asiento</th>
                <th>Precio</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (e of entradas; track e.entradaId) {
                <tr>
                  <td>#{{ e.eventoId }}</td>
                  <td>#{{ e.sectorId }}</td>
                  <td>
                    @if (e.fila) { Fila {{ e.fila }} · #{{ e.numero }} }
                    @else { General }
                  </td>
                  <td>$ {{ e.precioUnitario | number:'1.2-2' }}</td>
                  <td>
                    <span class="badge" [ngClass]="estadoBadge(e.estado)">{{ e.estado }}</span>
                  </td>
                  <td>
                    <button class="btn btn-outline-primary btn-sm" (click)="verQr(e)">
                      Ver QR
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (entradaQr) {
        <div class="modal d-block" style="background:rgba(0,0,0,.5)">
          <div class="modal-dialog modal-sm">
            <div class="modal-content">
              <div class="modal-header">
                <h6 class="modal-title">QR — {{ entradaQr.entradaId | slice:0:8 }}...</h6>
                <button class="btn-close" (click)="entradaQr = null"></button>
              </div>
              <div class="modal-body text-center">
                @if (entradaQr.estado === 'EMITIDA' && entradaQr.codigoQr) {
                  <img [src]="qrUrl(entradaQr.codigoQr)" alt="QR" style="width:200px">
                } @else {
                  <p class="text-muted">QR no disponible ({{ entradaQr.estado }})</p>
                }
                <dl class="row text-start mt-3 small mb-0">
                  <dt class="col-5">Estado</dt>
                  <dd class="col-7">{{ entradaQr.estado }}</dd>
                  <dt class="col-5">Precio</dt>
                  <dd class="col-7">$ {{ entradaQr.precioUnitario | number:'1.2-2' }}</dd>
                </dl>
              </div>
              @if (entradaQr.estado === 'EMITIDA') {
                <div class="modal-footer">
                  <button class="btn btn-outline-danger btn-sm" (click)="solicitar()">
                    Solicitar devolución
                  </button>
                </div>
              }
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class MyTicketsComponent implements OnInit {
  private http = inject(HttpClient);

  entradas:  Entrada[] = [];
  entradaQr: Entrada | null = null;
  loading = true;

  ngOnInit() {
    this.http.get<{ content: Entrada[] }>('http://localhost:8080/api/entradas')
      .subscribe({
        next:  r  => { this.entradas = r.content; this.loading = false; },
        error: () => { this.loading = false; }
      });
  }

  verQr(e: Entrada) { this.entradaQr = e; }

  qrUrl(codigo: string) {
    return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(codigo)}`;
  }

  solicitar() {
    if (!this.entradaQr) return;
    this.http.post('http://localhost:8080/api/devoluciones', { entradaId: this.entradaQr.entradaId })
      .subscribe({
        next: () => {
          if (this.entradaQr) this.entradaQr.estado = 'DEVOLUCION_SOLICITADA';
        },
        error: e => alert(e?.error?.message ?? 'Error al solicitar devolución')
      });
  }

  estadoBadge(estado: string): Record<string, boolean> {
    return {
      'bg-success':            estado === 'EMITIDA',
      'bg-secondary':          estado === 'USADA',
      'bg-warning text-dark':  estado === 'DEVOLUCION_SOLICITADA',
      'bg-info text-dark':     estado === 'REINTEGRADA',
    };
  }
}
