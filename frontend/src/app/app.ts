import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { SessionStore } from './session.store';
import { SpellStore } from './spell.store';
import { T } from './texts.hu';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly T = T;
  protected readonly session = inject(SessionStore);
  private readonly spell = inject(SpellStore);
  private readonly router = inject(Router);

  constructor() { void this.session.init(); }

  protected async logout() {
    await this.session.logout();
    await this.spell.clear();
    await this.router.navigateByUrl('/login');
  }
}
