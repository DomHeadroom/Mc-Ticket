import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthControllerService, LoginRequest } from '../generated';
import { tap } from 'rxjs';

export interface AuthState {
  token: string;
  email: string;
  role: string;
  fullName: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthControllerService);
  private readonly router = inject(Router);

  readonly state = signal<AuthState | null>(this.loadState());

  login(email: string, password: string) {
    return this.api
      .login({ email, password } as LoginRequest, 'body', false, {
        httpHeaderAccept: 'application/json' as '*/*',
      })
      .pipe(
        tap((res) => {
          const data = res as Record<string, string>;
          this.state.set({
            token: data['token'],
            email: data['email'],
            role: data['role'],
            fullName: data['fullName'],
          });
          localStorage.setItem('auth', JSON.stringify(this.state()));
        }),
      );
  }

  logout(): void {
    localStorage.removeItem('auth');
    this.state.set(null);
    this.router.navigate(['/login']);
  }

  get isLoggedIn(): boolean {
    return this.state() !== null;
  }

  private loadState(): AuthState | null {
    const raw = localStorage.getItem('auth');
    return raw ? (JSON.parse(raw) as AuthState) : null;
  }
}
