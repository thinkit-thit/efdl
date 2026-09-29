import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import background from '../../assets/metrica/images/p-1.png';

/** /cgu — conditions générales d'utilisation, page publique. */
export function CguPage() {
  useEffect(() => {
    document.body.classList.add('auth-page');
    return () => document.body.classList.remove('auth-page');
  }, []);

  return (
    <div style={{ backgroundImage: `url(${background})`, backgroundSize: 'cover', backgroundPosition: 'center center', minHeight: '100vh' }}>
      <div className="container-md">
        <div className="row vh-100 d-flex justify-content-center">
          <div className="col-12 align-self-center">
            <div className="card-body">
              <div className="row">
                <div className="col-lg-6 mx-auto">
                  <div className="card" style={{ padding: 10 }}>
                    <h2><strong>CONDITIONS GENERALES D'UTILISATION (CGU) DU SYSTÈME DIRECTEUR INFORMATIQUE DE FDL</strong></h2>
                    <p>
                      <strong>1. Acceptation des conditions</strong><br />
                      En vous connectant à ce système d’information, vous acceptez formellement les présentes conditions.
                    </p>
                    <p>
                      <strong>2. Accès et sécurité</strong><br />
                      L’accès au système est strictement réservé aux utilisateurs autorisés par la FDL. Vos identifiants et votre mot
                      de passe sont personnels et confidentiels. Vous êtes responsable de toutes les actions effectuées depuis votre
                      compte. En cas de perte ou de vol de vos accès, vous devez prévenir immédiatement le RSI de FDL.
                    </p>
                    <p>
                      <strong>3. Confidentialité et données</strong><br />
                      Vous vous engagez à respecter le secret professionnel et à ne pas divulguer les données sensibles ou personnelles
                      consultées sur la plateforme. Ces informations sont exclusivement réservées aux activités professionnelles liées
                      à la FDL.
                    </p>
                    <p>
                      <strong>4. Propriété et responsabilité</strong><br />
                      Tous les outils et contenus de cette plateforme appartiennent à la FDL. L’utilisateur s'engage à faire un usage
                      correct et honnête du système. La FDL met tout en œuvre pour assurer le bon fonctionnement de la plateforme, mais
                      ne peut être tenue responsable en cas de panne technique indépendante de sa volonté.
                    </p>
                    <Link to="/login" className="btn btn-primary">Retour vers la page d'authentification</Link>
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
