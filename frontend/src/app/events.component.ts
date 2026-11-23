import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface Evento {
  id: number;
  nombre: string;
  tipo: string;
  fechaHora: string;
  estado: string;
  ciudad?: string;
  precioDesde?: number;
}

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page">
      <h3 class="mb-3">Eventos</h3>

      <div class="row g-2 mb-4">
        <div class="col-md-4">
          <input class="form-control" [(ngModel)]="q" placeholder="Buscar nombre o artista..."
                 (input)="buscar()">
        </div>
        <div class="col-md-3">
          <input class="form-control" [(ngModel)]="ciudad" placeholder="Ciudad..."
                 (input)="buscar()">
        </div>
      </div>

      @if (loading) {
        <div class="text-center py-5">
          <div class="spinner-border text-primary"></div>
        </div>
      } @else if (eventos.length === 0) {
        <p class="text-center text-muted py-5">No se encontraron eventos.</p>
      } @else {
        <div class="row row-cols-1 row-cols-md-3 g-3">
          @for (ev of eventos; track ev.id) {
            <div class="col">
              <div class="card h-100 shadow-sm" style="cursor:pointer"
                   [routerLink]="['/events', ev.id]">
                <div class="card-body">
                  <h6 class="card-title">{{ ev.nombre }}</h6>
                  <p class="small text-muted mb-1">
                    {{ ev.fechaHora | date:'dd/MM/yyyy HH:mm' }}
                  </p>
                  @if (ev.ciudad) {
                    <p class="small text-muted mb-1">{{ ev.ciudad }}</p>
                  }
                  <span class="badge bg-success"
                        [class.bg-danger]="ev.estado === 'AGOTADO' || ev.estado === 'CANCELADO'"
                        [class.bg-secondary]="ev.estado === 'SUSPENDIDO' || ev.estado === 'FINALIZADO'">
                    {{ ev.estado }}
                  </span>
                  <span class="ms-2 small text-muted">{{ ev.tipo }}</span>
                </div>
                @if (ev.precioDesde != null) {
                  <div class="card-footer small fw-semibold text-primary bg-white">
                    Desde $ {{ ev.precioDesde | number:'1.0-0' }}
                  </div>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class EventsComponent implements OnInit {
  private http = inject(HttpClient);

  eventos: Evento[] = [];
  loading = true;
  q       = '';
  ciudad  = '';

  ngOnInit() { this.buscar(); }

  buscar() {
    this.loading = true;
    const params: Record<string, string> = { page: '0', size: '20' };
    if (this.q)      params['q']      = this.q;
    if (this.ciudad) params['ciudad'] = this.ciudad;

    this.http.get<{ content: Evento[] }>('http://localhost:8080/api/eventos', { params })
      .subscribe({
        next:  r  => { this.eventos = r.content; this.loading = false; },
        error: () => { this.loading = false; }
      });
  }

}
