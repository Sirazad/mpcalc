import { HttpInterceptorFn } from '@angular/common/http';

/** The page is Hungarian: ask the backend for Hungarian messages whatever language the browser prefers. */
export const langInterceptor: HttpInterceptorFn = (req, next) => next(req.clone({ setHeaders: { 'Accept-Language': 'hu' } }));
