import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

export interface UserInfo { userId: number; email: string; rol: string; }

const API = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private _user = signal<UserInfo | null>(null);
  user = this._user.asReadonly();

  constructor(private http: HttpClient, private router: Router) {
    const saved = localStorage.getItem('user');
    if (saved) this._user.set(JSON.parse(saved));
  }

  isAuth()          { return !!this._user(); }
  hasRol(rol: string) { return this._user()?.rol === rol; }
  getToken()        { return localStorage.getItem('token'); }

  login(email: string, password: string) {
    return this.http.post<{ token: string; userId: number; rol: string }>(
      `${API}/auth/login`, { email, password }
    ).pipe(tap(res => {
      localStorage.setItem('token', res.token);
      const u: UserInfo = { userId: res.userId, email, rol: res.rol };
      localStorage.setItem('user', JSON.stringify(u));
      this._user.set(u);
    }));
  }

  register(email: string, password: string, rol: string) {
    return this.http.post(`${API}/auth/register`, { email, password, rol });
  }

  logout() {
    localStorage.clear();
    this._user.set(null);
    this.router.navigate(['/login']);
  }
}
