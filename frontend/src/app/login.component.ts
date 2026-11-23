import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page" style="max-width:420px">
      <div class="card shadow-sm mt-5">
        <div class="card-body">
          <h4 class="card-title mb-4">Iniciar sesión</h4>
          <div class="mb-3">
            <label class="form-label">Email</label>
            <input class="form-control" type="email" [(ngModel)]="email" placeholder="usuario@email.com">
          </div>
          <div class="mb-3">
            <label class="form-label">Contraseña</label>
            <input class="form-control" type="password" [(ngModel)]="password">
          </div>
          @if (error) {
            <div class="alert alert-danger py-2 small">{{ error }}</div>
          }
          <button class="btn btn-primary w-100" (click)="login()" [disabled]="loading">
            @if (loading) { <span class="spinner-border spinner-border-sm me-1"></span> }
            Entrar
          </button>
          <p class="text-center mt-3 small">
            ¿No tenés cuenta? <a routerLink="/register">Registrarse</a>
          </p>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  private auth   = inject(AuthService);
  private router = inject(Router);

  email    = '';
  password = '';
  loading  = false;
  error    = '';

  login() {
    this.loading = true;
    this.error   = '';
    this.auth.login(this.email, this.password).subscribe({
      next:  () => this.router.navigate(['/events']),
      error: e  => { this.error = e?.error?.message ?? 'Credenciales incorrectas'; this.loading = false; }
    });
  }
}
