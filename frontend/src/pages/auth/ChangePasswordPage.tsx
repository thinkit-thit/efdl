import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import background from '../../assets/metrica/images/p-1.png';
import logoSm from '../../assets/metrica/images/logo-sm.png';
import { useAuth } from '../../contexts/AuthContext';
import { ApiError } from '../../services/apiClient';

/**
 * Changement de son mot de passe (hash BCrypt dans user_credential).
 * Obligatoire après une réinitialisation par un administrateur : le reste de l'application est alors bloqué,
 * y compris côté serveur. Présentée comme la page de connexion (hors layout).
 */
export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const forced = user?.mustChangePassword ?? false;
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    document.body.classList.add('auth-page');
    return () => document.body.classList.remove('auth-page');
  }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (next !== confirm) {
      setError('La confirmation ne correspond pas au nouveau mot de passe');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await changePassword(current, next);
      navigate('/home', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Changement impossible');
    } finally {
      setSubmitting(false);
    }
  };

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ backgroundImage: `url(${background})`, backgroundSize: 'cover', backgroundPosition: 'center center', minHeight: '100vh' }}>
      <div className="container-md">
        <div className="row vh-100 d-flex justify-content-center">
          <div className="col-12 align-self-center">
            <div className="card-body">
              <div className="row">
                <div className="col-lg-4 mx-auto">
                  <div className="card">
                    <div className="card-body p-0 auth-header-box">
                      <div className="text-center p-3">
                        <span className="logo logo-admin">
                          <img src={logoSm} height="50" alt="logo" className="auth-logo" />
                        </span>
                        <h4 className="mt-3 mb-1 fw-semibold text-white font-18">Changer le mot de passe</h4>
                        <p className="text-muted mb-0">{user?.displayName}</p>
                      </div>
                    </div>
                    <div className="card-body pt-0">
                      <form className="my-4" onSubmit={onSubmit} noValidate>
                        {forced && (
                          <div className="alert alert-warning py-2">
                            Votre mot de passe a été réinitialisé par un administrateur : choisissez-en un nouveau pour continuer.
                          </div>
                        )}
                        {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
                        <div className="form-group mb-2">
                          <label className="form-label" htmlFor="current">Mot de passe actuel</label>
                          <input type="password" className="form-control" id="current" autoComplete="current-password"
                            value={current} onChange={(e) => setCurrent(e.target.value)} required />
                        </div>
                        <div className="form-group mb-2">
                          <label className="form-label" htmlFor="next">Nouveau mot de passe</label>
                          <input type="password" className="form-control" id="next" autoComplete="new-password"
                            value={next} onChange={(e) => setNext(e.target.value)} required />
                          <small className="text-muted">8 caractères minimum, avec au moins une lettre et un chiffre.</small>
                        </div>
                        <div className="form-group mb-2">
                          <label className="form-label" htmlFor="confirm">Confirmation</label>
                          <input type="password" className="form-control" id="confirm" autoComplete="new-password"
                            value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
                        </div>
                        <div className="d-grid mt-3">
                          <button className="btn btn-primary" type="submit" disabled={submitting || !current || !next || !confirm}>
                            {submitting ? 'Enregistrement…' : 'Enregistrer'}
                          </button>
                        </div>
                        <div className="text-center mt-3">
                          {forced
                            ? <button type="button" className="btn btn-link text-muted" onClick={onLogout}>Se déconnecter</button>
                            : <Link to="/home" className="text-muted">Annuler</Link>}
                        </div>
                      </form>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
