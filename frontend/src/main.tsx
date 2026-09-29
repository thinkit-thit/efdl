import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

// Styles et polices du template Metrica, réutilisés tels quels.
import './assets/metrica/css/bootstrap.min.css';
import './assets/metrica/css/icons.min.css';
import './assets/metrica/css/app.min.css';

import { ConfirmProvider } from './components/ConfirmDialog';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthProvider } from './contexts/AuthContext';
import { AppRoutes } from './routes/AppRoutes';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary fullPage>
      <BrowserRouter>
        <ConfirmProvider>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </ConfirmProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>,
);
