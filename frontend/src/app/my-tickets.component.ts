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
  templateUrl: './my-tickets.component.html'
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
      'bg-success':           estado === 'EMITIDA',
      'bg-secondary':         estado === 'USADA',
      'bg-warning text-dark': estado === 'DEVOLUCION_SOLICITADA',
      'bg-info text-dark':    estado === 'REINTEGRADA',
    };
  }
}
