import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { Builder } from './pages/builder';
import { Characters } from './pages/characters';
import { Login } from './pages/login';
import { SessionStore } from './session.store';

/** Pages other than login need a logged-in player (the backend checks again on every call). */
const loggedIn: CanActivateFn = async () => {
  const session = inject(SessionStore);
  const router = inject(Router);   // inject() only works before the first await
  await session.init();
  return session.player() ? true : router.createUrlTree(['/login']);
};

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'characters' },
  { path: 'login', component: Login },
  { path: 'characters', component: Characters, canActivate: [loggedIn] },
  { path: 'builder/:id', component: Builder, canActivate: [loggedIn] },
  { path: '**', redirectTo: 'characters' },
];
