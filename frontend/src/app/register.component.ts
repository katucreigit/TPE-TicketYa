import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page" style="max-width:420px">
      <div class="card shadow-sm mt-5">
        <div class="card-body">
          <h4 class="card-title mb-4">Crear cuenta</h4>
          <div class="mb-3">
            <label class="form-label">Email</label>
            <input class="form-control" type="email" [(ngModel)]="email">
          </div>
          <div class="mb-3">
            <label class="form-label">Contraseña</label>
            <input class="form-control" type="password" [(ngModel)]="password">
          </div>
          <div class="mb-3">
            <label class="form-label">Rol</label>
            <select class="form-select" [(ngModel)]="rol">
              <option value="CLIENTE">Cliente</option>
              <option value="ORGANIZADOR">Organizador</option>
            </select>
          </div>
          @if (error) {
            <div class="alert alert-danger py-2 small">{{ error }}</div>
          }
          @if (ok) {
            <div class="alert alert-success py-2 small">
              Cuenta creada. <a routerLink="/login">Iniciar sesión</a>
            </div>
          }
          <button class="btn btn-primary w-100" (click)="register()" [disabled]="loading || ok">
            @if (loading) { <span class="spinner-border spinner-border-sm me-1"></span> }
            Registrarse
          </button>
          <p class="text-center mt-3 small">
            ¿Ya tenés cuenta? <a routerLink="/login">Iniciar sesión</a>
          </p>
        </div>
      </div>
    </div>
  `
})
export class RegisterComponent {
  private auth = inject(AuthService);

  email    = '';
  password = '';
  rol      = 'CLIENTE';
  loading  = false;
  error    = '';
  ok       = false;

  register() {
    this.loading = true;
    this.error   = '';
    this.auth.register(this.email, this.password, this.rol).subscribe({
      next:  () => { this.ok = true; this.loading = false; },
      error: e  => { this.error = e?.error?.message ?? 'Error al registrarse'; this.loading = false; }
    });
  }
}
