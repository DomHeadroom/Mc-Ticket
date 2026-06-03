import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const state = auth.state;

  if (state?.token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${state.token}` },
    });
  }

  return next(req);
};
