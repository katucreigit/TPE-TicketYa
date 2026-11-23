import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'events', pathMatch: 'full' },
  { path: 'login',    loadComponent: () => import('./login.component').then(m => m.LoginComponent) },
  { path: 'register', loadComponent: () => import('./register.component').then(m => m.RegisterComponent) },
  { path: 'events',   loadComponent: () => import('./events.component').then(m => m.EventsComponent) },
  { path: 'events/:id', loadComponent: () => import('./event-detail.component').then(m => m.EventDetailComponent) },
  { path: 'events/:id/checkout', canActivate: [authGuard], loadComponent: () => import('./checkout.component').then(m => m.CheckoutComponent) },
  { path: 'my-tickets', canActivate: [authGuard], loadComponent: () => import('./my-tickets.component').then(m => m.MyTicketsComponent) },
  { path: 'my-events',  canActivate: [authGuard], loadComponent: () => import('./my-events.component').then(m => m.MyEventsComponent) },
  { path: '**', redirectTo: 'events' }
];
