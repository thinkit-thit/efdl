import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import background from '../../assets/metrica/images/p-1.png';
import logoSm from '../../assets/metrica/images/logo-sm.png';
import { useAuth } from '../../contexts/AuthContext';
import { ApiError } from '../../services/apiClient';

/**
 * Page de connexion :
 * identifiant (nom d'utilisateur, e-mail ou téléphone) + mot de passe, redirection vers la page demandée ou /home.
 */
export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/home';
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    document.body.classList.add('auth-page');
    return () => document.body.classList.remove('auth-page');
  }, []);

  if (user) {
    return <Navigate to={from} replace />;
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!identifier.trim() || !password) {
      setError('Saisissez votre login et votre mot de passe');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await login(identifier.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Connexion impossible');
    } finally {
      setSubmitting(false);
    }
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
                        <p className="mt-3 mb-1 fw-semibold text-white font-12">Connectez vous pour acceder à votre compte e-FDL</p>
                      </div>
                    </div>
                    <div className="card-body pt-0">
                      <form onSubmit={onSubmit} noValidate>
                        {error && <div className="alert alert-danger py-2 mt-3" role="alert">{error}</div>}
                        <div className="form-group mb-2">
                          <label className="form-label" htmlFor="username">Login</label>
                          <input
                            type="text" className="form-control" id="username" name="username" autoComplete="username"
                            placeholder="Votre login ou email"
                            value={identifier} onChange={(e) => setIdentifier(e.target.value)} required
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label" htmlFor="userpassword">Mot de passe</label>
                          <input
                            type="password" className="form-control" name="password" id="userpassword" autoComplete="current-password"
                            placeholder="Votre mot de passe"
                            value={password} onChange={(e) => setPassword(e.target.value)} required
                          />
                        </div>
                        <div className="form-group row mt-3">
                          <div className="col-sm-6"></div>
                          <div className="col-sm-6 text-end">
                            <Link to="/forgotPwd" className="text-muted font-13">
                              <i className="dripicons-lock"></i> Mot de passe oublié?
                            </Link>
                          </div>
                        </div>
                        {/* Acceptation des conditions d'utilisation. */}
                        <div className="form-group row mt-3">
                          <div className="col-sm-12" style={{ textAlign: 'center' }}>
                            En vous connectant à cette plateforme, vous acceptez les conditions d'utilisations.{' '}
                            <Link to="/cgu" className="text-muted font-13"><i className="dripicons-lock"></i>Voir conditions d'utilisations</Link>
                          </div>
                        </div>
                        <div className="form-group mb-0 row">
                          <div className="col-12">
                            <div className="d-grid mt-3">
                              <button className="btn btn-primary" type="submit" disabled={submitting}>
                                {submitting ? 'Connexion…' : 'Connexion'}
                              </button>
                            </div>
                          </div>
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
