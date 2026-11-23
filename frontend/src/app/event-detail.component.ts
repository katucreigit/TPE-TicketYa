import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { AuthService } from './auth.service';

interface Evento {
  id: number;
  nombre: string;
  tipo: string;
  estado: string;
  fechaHora: string;
  descripcion?: string;
}
interface Sector {
  sectorId: number;
  nombre: string;
  tipo: string;
  precio: number;
  capacidad: number;
}

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page">
      @if (loading) {
        <div class="text-center py-5">
          <div class="spinner-border text-primary"></div>
        </div>
      } @else if (evento) {
        <a routerLink="/events" class="btn btn-link px-0 mb-3">← Volver a eventos</a>

        <div class="row g-4">
          <div class="col-md-8">
            <h3>{{ evento.nombre }}</h3>
            <p>
              <span class="badge bg-success"
                    [class.bg-danger]="evento.estado === 'AGOTADO' || evento.estado === 'CANCELADO'"
                    [class.bg-secondary]="evento.estado === 'SUSPENDIDO' || evento.estado === 'FINALIZADO'">
                {{ evento.estado }}
              </span>
              <span class="ms-2 text-muted">{{ evento.tipo }}</span>
            </p>
            <p>Fecha: {{ evento.fechaHora | date:'dd/MM/yyyy HH:mm' }}</p>
            @if (evento.descripcion) {
              <p class="text-muted">{{ evento.descripcion }}</p>
            }
          </div>

          <div class="col-md-4">
            <div class="card shadow-sm">
              <div class="card-header fw-semibold">Sectores y precios</div>
              <div class="list-group list-group-flush">
                @for (s of sectores; track s.sectorId) {
                  <div class="list-group-item d-flex justify-content-between align-items-center"
                       style="cursor:pointer"
                       [class.active]="selected?.sectorId === s.sectorId"
                       (click)="select(s)">
                    <div>
                      <div class="fw-semibold">{{ s.nombre }}</div>
                      <small>{{ s.tipo === 'NUMERADO' ? 'Numerado' : 'General' }}</small>
                    </div>
                    <span class="fw-bold">$ {{ s.precio | number:'1.0-0' }}</span>
                  </div>
                }
              </div>
              <div class="card-body">
                @if (auth.isAuth() && auth.hasRol('CLIENTE')) {
                  <a class="btn btn-primary w-100"
                     [class.disabled]="!selected"
                     [routerLink]="selected ? ['/events', evento.id, 'checkout'] : null"
                     [queryParams]="{ sector: selected?.sectorId }">
                    {{ selected ? 'Comprar — ' + selected.nombre : 'Elegí un sector' }}
                  </a>
                } @else if (!auth.isAuth()) {
                  <a class="btn btn-outline-primary w-100" routerLink="/login">
                    Iniciá sesión para comprar
                  </a>
                } @else {
                  <p class="text-muted small mb-0 text-center">
                    Solo los clientes pueden comprar.
                  </p>
                }
              </div>
            </div>
          </div>
        </div>
      } @else {
        <div class="alert alert-warning">Evento no encontrado.</div>
      }
    </div>
  `
})
export class EventDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private http  = inject(HttpClient);
  auth = inject(AuthService);

  evento:   Evento | null  = null;
  sectores: Sector[]       = [];
  selected: Sector | null  = null;
  loading = true;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    forkJoin({
      evento:   this.http.get<Evento>(`http://localhost:8080/api/eventos/${id}`),
      sectores: this.http.get<Sector[]>(`http://localhost:8080/api/eventos/${id}/sectores`)
    }).subscribe({
      next:  ({ evento, sectores }) => {
        this.evento   = evento;
        this.sectores = sectores;
        this.loading  = false;
      },
      error: () => { this.loading = false; }
    });
  }

  select(s: Sector) {
    this.selected = this.selected?.sectorId === s.sectorId ? null : s;
  }
}
