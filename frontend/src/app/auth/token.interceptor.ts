import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

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

  return next(req.clone({ headers }));
};
