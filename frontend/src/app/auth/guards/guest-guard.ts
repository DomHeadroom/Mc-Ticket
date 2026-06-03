import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../auth.service';

export const guestGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  if (!auth.isLoggedIn) return true;
  return inject(Router).createUrlTree(['/']);
};
