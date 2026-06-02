import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Home } from './pages/home/home';
import { authGuard } from './auth/guards/auth-guard';
import { guestGuard } from './auth/guards/guest-guard';

export const routes: Routes = [
  {
    path: '',
    canMatch: [guestGuard],
    children: [
      { path: 'login', component: Login },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  {
    path: '',
    canMatch: [authGuard],
    children: [
      { path: 'home', component: Home },
      { path: '', redirectTo: 'home', pathMatch: 'full' },
    ],
  },
];
