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
  templateUrl: './events.component.html'
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
