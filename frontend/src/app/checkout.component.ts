import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface Sector  { sectorId: number; nombre: string; tipo: string; precio: number; }
interface Reserva { reservaId: string; cantidad: number; expiraEn: string; }
interface Compra  { compraId: string; estado: string; }
interface Asiento { fila: string; numero: number; estado: string; }

const API = 'http://localhost:8080/api';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './checkout.component.html'
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

  asientosDisponibles: Asiento[] = [];
  asientosSeleccionados: { fila: string; numero: number }[] = [];
  loadingAsientos = false;

  ngOnInit() {
    const eventoId = this.route.snapshot.paramMap.get('id')!;
    const sectorId = this.route.snapshot.queryParamMap.get('sector')!;

    this.http.get<Sector[]>(`${API}/eventos/${eventoId}/sectores`).subscribe({
      next: ss => {
        this.sector  = ss.find(s => String(s.sectorId) === sectorId) ?? ss[0] ?? null;
        this.loading = false;
        if (this.sector?.tipo === 'NUMERADO') {
          this.cargarAsientos(eventoId, String(this.sector.sectorId));
        }
      },
      error: () => { this.loading = false; this.router.navigate(['/events']); }
    });
  }

  private cargarAsientos(eventoId: string, sectorId: string) {
    this.loadingAsientos = true;
    this.http.get<Asiento[]>(`${API}/disponibilidad/eventos/${eventoId}/sectores/${sectorId}/asientos`)
      .subscribe({
        next:  asientos => {
          this.asientosDisponibles = asientos.filter(a => a.estado === 'LIBRE');
          this.loadingAsientos = false;
        },
        error: () => { this.loadingAsientos = false; }
      });
  }

  filas(): string[] {
    return [...new Set(this.asientosDisponibles.map(a => a.fila))].sort();
  }

  asientosDeFila(fila: string): Asiento[] {
    return this.asientosDisponibles
      .filter(a => a.fila === fila)
      .sort((a, b) => a.numero - b.numero);
  }

  isSeleccionado(a: Asiento): boolean {
    return this.asientosSeleccionados.some(s => s.fila === a.fila && s.numero === a.numero);
  }

  toggleAsiento(a: Asiento) {
    if (this.isSeleccionado(a)) {
      this.asientosSeleccionados = this.asientosSeleccionados
        .filter(s => !(s.fila === a.fila && s.numero === a.numero));
    } else {
      this.asientosSeleccionados = [...this.asientosSeleccionados, { fila: a.fila, numero: a.numero }];
    }
  }

  reservar() {
    if (!this.sector) return;
    if (this.sector.tipo === 'NUMERADO' && this.asientosSeleccionados.length === 0) return;
    this.reservando   = true;
    this.errorReserva = '';
    const eventoId    = Number(this.route.snapshot.paramMap.get('id'));

    const body = this.sector.tipo === 'NUMERADO'
      ? { eventoId, sectorId: this.sector.sectorId, asientos: this.asientosSeleccionados }
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
    }, {
      headers: { 'Idempotency-Key': crypto.randomUUID() }
    }).subscribe({
      next:  c => { this.compra = c; this.pagando = false; },
      error: e => { this.errorPago = e?.error?.message ?? 'Error en el pago'; this.pagando = false; }
    });
  }
}
