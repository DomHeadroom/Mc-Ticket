import { CanMatchFn } from '@angular/router';

export const guestGuard: CanMatchFn = (route, segments) => {
  return true;
};
