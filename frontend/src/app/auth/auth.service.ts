import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthControllerService, LoginRequest } from '../generated';
import { map, Observable } from 'rxjs';

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

  login(email: string, password: string): Observable<object> {
    return this.api
      .login({ email, password } as LoginRequest, 'body', false, {
        httpHeaderAccept: 'application/json' as '*/*',
      })
      .pipe(
        map((res) => {
          this.setSession(res as Record<string, string>);
          return res;
        }),
      );
  }

  logout(): void {
    localStorage.removeItem('auth');
    this.router.navigate(['/login']);
  }

  get state(): AuthState | null {
    const raw = localStorage.getItem('auth');
    return raw ? (JSON.parse(raw) as AuthState) : null;
  }

  get isLoggedIn(): boolean {
    const state = this.state;
    return state !== null && !!state.token;
  }

  private setSession(res: Record<string, string>): void {
    const state: AuthState = {
      token: res['token'],
      email: res['email'],
      role: res['role'],
      fullName: res['fullName'],
    };
    localStorage.setItem('auth', JSON.stringify(state));
  }
}
