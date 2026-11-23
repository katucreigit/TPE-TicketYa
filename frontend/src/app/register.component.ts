import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.component.html'
})
export class RegisterComponent {
  private auth = inject(AuthService);

  email    = '';
  password = '';
  readonly rol = 'CLIENTE';
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
