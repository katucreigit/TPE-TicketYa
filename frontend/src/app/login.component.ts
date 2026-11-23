import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html'
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
