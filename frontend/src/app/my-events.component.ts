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
  templateUrl: './my-events.component.html'
})
export class MyEventsComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  eventos: Evento[] = [];
  loading = true;

  ngOnInit() {
    const organizadorId = this.auth.user()?.userId;
    this.http.get<{ content: Evento[] }>(
      'http://localhost:8080/api/eventos',
      { params: { organizadorId: String(organizadorId), page: '0', size: '50' } }
    ).subscribe({
      next:  r  => { this.eventos = r.content; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }
}
