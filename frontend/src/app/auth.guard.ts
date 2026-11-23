import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  if (inject(AuthService).isAuth()) return true;
  inject(Router).navigate(['/login']);
  return false;
};
