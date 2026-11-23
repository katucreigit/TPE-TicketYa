import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

interface Evento {
  id: number;
  nombre: string;
  tipo: string;
  fechaHora: string;
  estado: string;
  ciudad?: string;
}

@Component({
  selector: 'app-my-events',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page">
      <h3 class="mb-4">Mis Eventos</h3>

      @if (loading) {
        <div class="text-center py-5"><div class="spinner-border text-primary"></div></div>
      } @else if (eventos.length === 0) {
        <p class="text-center text-muted py-5">No tenés eventos creados.</p>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle">
            <thead class="table-light">
              <tr>
                <th>Nombre</th>
                <th>Tipo</th>
                <th>Fecha</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (ev of eventos; track ev.id) {
                <tr>
                  <td class="fw-semibold">{{ ev.nombre }}</td>
                  <td class="text-muted small">{{ ev.tipo }}</td>
                  <td class="small">{{ ev.fechaHora | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>
                    <span class="badge bg-success"
                          [class.bg-danger]="ev.estado === 'AGOTADO' || ev.estado === 'CANCELADO'"
                          [class.bg-secondary]="ev.estado === 'SUSPENDIDO' || ev.estado === 'FINALIZADO'"
                          [class.bg-info]="ev.estado === 'BORRADOR'">
                      {{ ev.estado }}
                    </span>
                  </td>
                  <td>
                    <a [routerLink]="['/events', ev.id]" class="btn btn-outline-secondary btn-sm">
                      Ver detalle
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class MyEventsComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  eventos: Evento[] = [];
  loading = true;

  ngOnInit() {
    const organizadorId = this.auth.user()?.userId;
    this.http.get<{ content: Evento[] }>(
      `http://localhost:8080/api/eventos`,
      { params: { organizadorId: String(organizadorId), page: '0', size: '50' } }
    ).subscribe({
      next:  r  => { this.eventos = r.content; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

}
