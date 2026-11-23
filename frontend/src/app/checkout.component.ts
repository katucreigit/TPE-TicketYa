import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface Sector  { sectorId: number; nombre: string; tipo: string; precio: number; }
interface Reserva { reservaId: string; cantidad: number; expiraEn: string; }
interface Compra  { compraId: string; estado: string; }

const API = 'http://localhost:8080/api';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page" style="max-width:600px">
      <h4 class="mb-4">Checkout</h4>

      @if (loading) {
        <div class="text-center py-5"><div class="spinner-border text-primary"></div></div>

      } @else if (compra) {
        <div class="card shadow-sm text-center">
          <div class="card-body py-5">
            @if (compra.estado === 'APROBADA') {
              <div class="fs-1 mb-2">✅</div>
              <h4 class="text-success">¡Compra aprobada!</h4>
              <a routerLink="/my-tickets" class="btn btn-primary mt-3">Ver mis entradas</a>
            } @else {
              <div class="fs-1 mb-2">❌</div>
              <h4 class="text-danger">Pago {{ compra.estado }}</h4>
              <p class="text-muted">Los lugares fueron liberados.</p>
              <a routerLink="/events" class="btn btn-outline-secondary mt-2">Volver a eventos</a>
            }
          </div>
        </div>

      } @else if (!reserva) {
        <div class="card shadow-sm mb-3">
          <div class="card-header">1. Reservar lugar</div>
          <div class="card-body">
            @if (sector) {
              <p><strong>{{ sector.nombre }}</strong>
                <span class="text-muted ms-2 small">
                  ({{ sector.tipo === 'NUMERADO' ? 'Numerado' : 'General' }})
                </span>
              </p>
              <p class="text-primary fw-bold">
                Precio: $ {{ sector.precio | number:'1.0-0' }} por entrada
              </p>
            }
            @if (sector?.tipo === 'NO_NUMERADO') {
              <div class="mb-2" style="max-width:160px">
                <label class="form-label">Cantidad</label>
                <input type="number" class="form-control" [(ngModel)]="cantidad" min="1" max="4">
              </div>
            }
            @if (errorReserva) {
              <div class="alert alert-danger py-2 small">{{ errorReserva }}</div>
            }
          </div>
          <div class="card-footer">
            <button class="btn btn-primary" (click)="reservar()" [disabled]="reservando">
              @if (reservando) { <span class="spinner-border spinner-border-sm me-1"></span> }
              Reservar (10 min)
            </button>
          </div>
        </div>

      } @else {
        <div class="alert alert-success py-2 mb-3">
          ✅ Reserva activa — expira {{ reserva.expiraEn | date:'HH:mm:ss' }}
        </div>
        <div class="card shadow-sm">
          <div class="card-header">2. Pagar</div>
          <div class="card-body">
            <p>Total:
              <strong>$ {{ (sector?.precio || 0) * reserva.cantidad | number:'1.2-2' }}</strong>
              ({{ reserva.cantidad }} entrada/s)
            </p>
            <div class="mb-3">
              <label class="form-label">Medio de pago</label>
              <select class="form-select" [(ngModel)]="tipoPago" style="max-width:280px">
                <option value="TARJETA_CREDITO">Tarjeta de crédito</option>
                <option value="TARJETA_DEBITO">Tarjeta de débito</option>
                <option value="TRANSFERENCIA">Transferencia</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">Token de pago</label>
              <input class="form-control" [(ngModel)]="token"
                     placeholder="tok_ok / tok_rechazado / tok_timeout"
                     style="max-width:320px">
            </div>
            <div class="mb-3">
              <label class="form-label">Últimos 4 dígitos</label>
              <input class="form-control" [(ngModel)]="ultimos4" maxlength="4" style="max-width:120px">
            </div>
            @if (errorPago) {
              <div class="alert alert-danger py-2 small">{{ errorPago }}</div>
            }
          </div>
          <div class="card-footer">
            <button class="btn btn-success" (click)="pagar()" [disabled]="pagando">
              @if (pagando) { <span class="spinner-border spinner-border-sm me-1"></span> }
              Confirmar pago
            </button>
          </div>
        </div>
      }
    </div>
  `
})
export class CheckoutComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private router = inject(Router);
  private http   = inject(HttpClient);

  sector:   Sector | null  = null;
  reserva:  Reserva | null = null;
  compra:   Compra | null  = null;
  cantidad  = 1;
  tipoPago  = 'TARJETA_CREDITO';
  token     = 'tok_ok';
  ultimos4  = '1234';
  loading   = true;
  reservando = false;
  pagando    = false;
  errorReserva = '';
  errorPago    = '';

  ngOnInit() {
    const eventoId  = this.route.snapshot.paramMap.get('id')!;
    const sectorId  = this.route.snapshot.queryParamMap.get('sector')!;

    this.http.get<Sector[]>(`${API}/eventos/${eventoId}/sectores`).subscribe({
      next: ss => {
        this.sector  = ss.find(s => String(s.sectorId) === sectorId) ?? ss[0] ?? null;
        this.loading = false;
      },
      error: () => { this.loading = false; this.router.navigate(['/events']); }
    });
  }

  reservar() {
    if (!this.sector) return;
    this.reservando   = true;
    this.errorReserva = '';
    const eventoId    = Number(this.route.snapshot.paramMap.get('id'));

    const body = this.sector.tipo === 'NUMERADO'
      ? { eventoId, sectorId: this.sector.sectorId, asientos: [] }
      : { eventoId, sectorId: this.sector.sectorId, cantidad: this.cantidad };

    this.http.post<Reserva>(`${API}/reservas`, body).subscribe({
      next:  r => { this.reserva = r; this.reservando = false; },
      error: e => { this.errorReserva = e?.error?.message ?? 'Error al reservar'; this.reservando = false; }
    });
  }

  pagar() {
    if (!this.reserva) return;
    this.pagando   = true;
    this.errorPago = '';

    this.http.post<Compra>(`${API}/compras`, {
      reservaId: this.reserva.reservaId,
      medioPago: { tipo: this.tipoPago, token: this.token, ultimos4: this.ultimos4 }
    }).subscribe({
      next:  c  => { this.compra = c; this.pagando = false; },
      error: e  => { this.errorPago = e?.error?.message ?? 'Error en el pago'; this.pagando = false; }
    });
  }
}
