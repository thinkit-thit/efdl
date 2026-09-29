import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { AppLayout } from '../layouts/AppLayout';
import { CguPage } from '../pages/auth/CguPage';
import { ChangePasswordPage } from '../pages/auth/ChangePasswordPage';
import { ProfilePage } from '../pages/auth/ProfilePage';
import { NotificationsPage } from '../pages/auth/NotificationsPage';
import { AuditPage } from '../pages/admin/AuditPage';
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { HomePage } from '../pages/home/HomePage';
import { LocalisationDashboardPage } from '../pages/localisation/LocalisationDashboardPage';
import { GroupDetailPage, GroupsPage } from '../pages/admin/GroupsPages';
import { MenuDetailPage, MenusPage } from '../pages/admin/MenusPages';
import { SchoolDetailPage, SchoolsPage } from '../pages/admin/SchoolsPages';
import { UserDetailPage, UsersPage } from '../pages/admin/UsersPages';
import { ParamEditPage, ParamListPage } from '../pages/params/ParamPages';
import { PARAM_SCREENS } from '../services/paramApi';
import { ValidationDetailPage, ValidationListPage } from '../pages/validation/ValidationPages';
import { ProgrammeDashboardPage, ProgressPage, SubProgrammeDashboardPage } from '../pages/project/TrackingPages';
import { ParamEditPage as ProjectViewPage } from '../pages/params/ParamPages';

export const CHANGE_PASSWORD_PATH = '/account/password';

/**
 * Redirige vers /login si aucune session, et vers le changement de mot de passe s'il est obligatoire
 * (le backend reste la vraie barrière de sécurité).
 */
function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return <div className="d-flex vh-100 align-items-center justify-content-center"><div className="spinner-border text-primary" role="status" /></div>;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (user.mustChangePassword && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace />;
  }
  return <>{children}</>;
}

/**
 * Routes des écrans : les menus (collection Menu) et
 * les droits (regex UserGroup.allowedPaths) s'appliquent sans modification des données.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cgu" element={<CguPage />} />
      <Route path="/forgotPwd" element={<ForgotPasswordPage />} />
      <Route path={CHANGE_PASSWORD_PATH} element={<RequireAuth><ChangePasswordPage /></RequireAuth>} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/" element={<Navigate to="/home" replace />} />
        <Route path="/home" element={<HomePage />} />
        <Route path="/account/profile" element={<ProfilePage />} />
        <Route path="/account/notifications" element={<NotificationsPage />} />
        <Route path="/admin/historique" element={<AuditPage />} />

        {/* Administration */}
        <Route path="/admin/user/group/list" element={<GroupsPage />} />
        <Route path="/admin/user/group/edit/:id" element={<GroupDetailPage />} />
        <Route path="/admin/menus" element={<MenusPage />} />
        <Route path="/admin/menu/edit/:id" element={<MenuDetailPage />} />
        <Route path="/admin/school/list" element={<SchoolsPage />} />
        <Route path="/admin/school/edit/:id" element={<SchoolDetailPage />} />
        <Route path="/admin/user/administration/list" element={<UsersPage />} />
        <Route path="/admin/user/administration/edit/:id" element={<UserDetailPage />} />

        {/* Écrans génériques : paramètres (projets, localisation, validateurs, KPI) et FDL PTA */}
        {PARAM_SCREENS.map((s) => (
          <Route key={s.key} path={s.list} element={<ParamListPage key={s.key} paramKey={s.key} />} />
        ))}
        {PARAM_SCREENS.map((s) => (
          <Route key={s.key + '-edit'} path={s.edit} element={<ParamEditPage key={s.key} paramKey={s.key} />} />
        ))}

        {/* Circuit de validation des factures */}
        <Route path="/admin/project/validationPaiements" element={<ValidationListPage />} />
        <Route path="/admin/project/validationPaiement/edit/:id" element={<ValidationDetailPage />} />

        {/* Suivi d'avancement et tableaux de bord FDL PROJET */}
        <Route path="/admin/project/programme/avancement/:id" element={<ProgressPage />} />
        <Route path="/admin/project/programme/:programmeId/avancement/:id" element={<ProgressPage />} />
        <Route path="/admin/project/programme/:programmeId/subProgramme/:subProgrammeId/avancement/:id" element={<ProgressPage />} />
        <Route path="/admin/project/programme/dashboard/:id" element={<ProgrammeDashboardPage />} />
        <Route path="/admin/project/programme/:programmeId/dashboard/:id" element={<SubProgrammeDashboardPage />} />
        {/* « Voir » un sous-projet : sa fiche (et ses contrats) */}
        <Route path="/admin/project/project/view/:id" element={<ProjectViewPage key="view" paramKey="allProjects" />} />

        {/* Tableau de bord des localisations */}
        <Route path="/admin/dashboard/election/tbs" element={<LocalisationDashboardPage />} />

        {/* Adresse inconnue : retour à l'accueil */}
        <Route path="*" element={<Navigate to="/home" replace />} />
      </Route>
    </Routes>
  );
}
