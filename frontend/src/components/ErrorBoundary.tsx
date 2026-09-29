import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** Quand cette valeur change (ex. l'URL), l'erreur est oubliée et la page est réaffichée. */
  resetKey?: string;
  /** Écran pleine page (erreur hors du layout) plutôt qu'une carte dans le contenu. */
  fullPage?: boolean;
}

interface State {
  error: Error | null;
}

/**
 * Écran d'erreur à la place d'une page blanche quand l'affichage d'une page échoue.
 * Le détail technique n'est montré qu'en développement.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Erreur d\'affichage', error, info.componentStack);
  }

  componentDidUpdate(previous: Props) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const content = (
      <div className="card">
        <div className="card-body text-center py-5">
          <i className="las la-exclamation-triangle text-warning" style={{ fontSize: 48 }}></i>
          <h4 className="mt-3">Cette page n'a pas pu s'afficher</h4>
          <p className="text-muted mb-4">
            Une erreur inattendue s'est produite. Rechargez la page ; si le problème persiste, signalez-le avec l'adresse de la page.
          </p>
          <button type="button" className="btn btn-primary me-2" onClick={() => window.location.reload()}>
            <i className="las la-redo-alt me-1"></i>Recharger la page
          </button>
          <a className="btn btn-outline-secondary" href="/home">Retour à l'accueil</a>
          {import.meta.env.DEV && (
            <pre className="text-start text-danger bg-light p-3 mt-4 mb-0 small" style={{ whiteSpace: 'pre-wrap' }}>
              {error.name}: {error.message}
            </pre>
          )}
        </div>
      </div>
    );

    if (!this.props.fullPage) return <div className="pt-3">{content}</div>;
    return (
      <div className="container-md">
        <div className="row vh-100 d-flex justify-content-center">
          <div className="col-lg-6 align-self-center">{content}</div>
        </div>
      </div>
    );
  }
}
