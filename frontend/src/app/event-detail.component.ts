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
  templateUrl: './event-detail.component.html'
})
export class EventDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private http  = inject(HttpClient);
  auth = inject(AuthService);

  evento:   Evento | null  = null;
  sectores: Sector[]       = [];
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
}
