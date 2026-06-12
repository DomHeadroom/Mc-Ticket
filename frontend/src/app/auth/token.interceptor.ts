import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { catchError, throwError } from 'rxjs';

export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const state = auth.state();

  let headers = req.headers;

  if (state?.token) {
    headers = headers.set('Authorization', `Bearer ${state.token}`);
  }

  if (!headers.has('Accept')) {
    headers = headers.set('Accept', 'application/json');
  }

  return next(req.clone({ headers })).pipe(
    catchError((err) => {
      if (err.status === 401 && auth.isLoggedIn) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
