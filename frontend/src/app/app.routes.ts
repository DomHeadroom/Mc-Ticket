import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Home } from './pages/home/home';
import { authGuard } from './auth/guards/auth-guard';
import { adminGuard } from './auth/guards/admin-guard';
import { guestGuard } from './auth/guards/guest-guard';

export const routes: Routes = [
  {
    path: '',
    component: Home,
    canActivate: [authGuard],
  },
  {
    path: 'tickets',
    loadComponent: () => import('./pages/ticket-list/ticket-list').then(m => m.TicketList),
    canActivate: [authGuard, adminGuard],
  },
  { path: 'login', component: Login, canActivate: [guestGuard] },
];
