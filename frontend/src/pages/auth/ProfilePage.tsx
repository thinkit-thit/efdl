import { Link } from 'react-router-dom';
import { PageTitle } from '../../components/PageTitle';
import { PhotoEditor } from '../../components/PhotoEditor';
import { Card, FormRow } from '../../components/common';
import { useAuth } from '../../contexts/AuthContext';

/** /account/profile — « Mon profil » : sa photo de profil et accès au changement de mot de passe. */
export function ProfilePage() {
  const { user } = useAuth();
  if (!user) return null;
  return (
    <>
      <PageTitle title="Mon profil" breadcrumb={['Compte', 'Mon profil']} />
      <Card title={user.displayName ?? user.username}>
        <FormRow label="Photo"><PhotoEditor userId={user.externalId} self /></FormRow>
        <FormRow label="Identifiant"><input className="form-control" readOnly value={user.username ?? ''} /></FormRow>
        <FormRow label="Application"><input className="form-control" readOnly value={user.school?.name ?? ''} /></FormRow>
        <FormRow label="Mot de passe">
          <Link to="/account/password" className="btn btn-outline-secondary">Changer le mot de passe</Link>
        </FormRow>
      </Card>
    </>
  );
}
