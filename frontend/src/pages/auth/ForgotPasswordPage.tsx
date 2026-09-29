import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import background from '../../assets/metrica/images/p-1.png';
import logoSm from '../../assets/metrica/images/logo-sm.png';
import { api, ApiError } from '../../services/apiClient';

/**
 * /forgotPwd — « Réinitialiser votre mot de passe » : l'identifiant, puis le code reçu
 * par e-mail et le nouveau mot de passe. Le message est le même que le compte existe ou non.
 */
export function ForgotPasswordPage() {
  const [step, setStep] = useState<'request' | 'confirm' | 'done'>('request');
  const [identifier, setIdentifier] = useState('');
  const [code, setCode] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    document.body.classList.add('auth-page');
    return () => document.body.classList.remove('auth-page');
  }, []);

  const send = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api<void>('/api/auth/password-reset/request', { method: 'POST', body: JSON.stringify({ identifier: identifier.trim() }) });
      setStep('confirm');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Demande impossible');
    } finally {
      setBusy(false);
    }
  };

  const validate = async (e: FormEvent) => {
    e.preventDefault();
    if (next !== confirm) {
      setError('La confirmation ne correspond pas au nouveau mot de passe');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api<void>('/api/auth/password-reset/confirm', {
        method: 'POST', body: JSON.stringify({ identifier: identifier.trim(), code: code.trim(), newPassword: next }),
      });
      setStep('done');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Validation impossible');
    } finally {
      setBusy(false);
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
                        <h4 className="mt-3 mb-1 fw-semibold text-white font-18">Réinitialiser votre mot de passe</h4>
                      </div>
                    </div>
                    <div className="card-body pt-0">
                      {error && <div className="alert alert-danger py-2 mt-3" role="alert">{error}</div>}
                      {step === 'request' && (
                        <form className="my-4" onSubmit={send}>
                          <div className="form-group mb-2">
                            <label className="form-label" htmlFor="identifier">Identifiant</label>
                            <input className="form-control" id="identifier" autoComplete="username"
                              placeholder="Nom d'utilisateur, e-mail ou téléphone" value={identifier}
                              onChange={(e) => setIdentifier(e.target.value)} required />
                            <small className="text-muted">Un code vous sera envoyé à l'adresse e-mail de votre compte.</small>
                          </div>
                          <div className="d-grid mt-3">
                            <button className="btn btn-primary" type="submit" disabled={busy || !identifier.trim()}>
                              {busy ? 'Envoi…' : 'Réinitialiser'}
                            </button>
                          </div>
                        </form>
                      )}
                      {step === 'confirm' && (
                        <form className="my-4" onSubmit={validate}>
                          <div className="alert alert-info py-2">
                            Si un compte correspond à « {identifier.trim()} » et possède une adresse e-mail, un code vient d'y être
                            envoyé (valable 15 minutes). Sans adresse e-mail, demandez à un administrateur de réinitialiser votre
                            mot de passe.
                          </div>
                          <div className="form-group mb-2">
                            <label className="form-label" htmlFor="code">Tapez le code que vous avez reçu</label>
                            <input className="form-control" id="code" inputMode="numeric" autoComplete="one-time-code"
                              value={code} onChange={(e) => setCode(e.target.value)} required />
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
                            <button className="btn btn-primary" type="submit" disabled={busy || !code || !next || !confirm}>
                              {busy ? 'Validation…' : 'Valider'}
                            </button>
                          </div>
                          <div className="text-center mt-2">
                            <button type="button" className="btn btn-link text-muted" onClick={() => { setStep('request'); setError(null); }}>
                              Renvoyer un code
                            </button>
                          </div>
                        </form>
                      )}
                      {step === 'done' && (
                        <div className="my-4">
                          <div className="alert alert-success py-2">Votre mot de passe a été modifié. Vous pouvez vous connecter.</div>
                        </div>
                      )}
                      <div className="text-center mb-3">
                        <Link to="/login" className="text-muted">Retour vers la page d'authentification</Link>
                      </div>
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
