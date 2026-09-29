import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { MadagascarMap } from '../../components/MadagascarMap';
import { PageTitle } from '../../components/PageTitle';
import { Card, ErrorAlert, Loading, useApi } from '../../components/common';
import { trackingApi } from '../../services/trackingApi';

/**
 * Accueil « NOS PROGRAMMES » (/home) : budget et avancement de chaque programme, carte des districts de
 * Madagascar colorés selon le programme de leurs sous-projets ; un clic ouvre les sous-projets du district.
 * Filtre par programme : ?programmeId=….
 */
export function HomePage() {
  const [search] = useSearchParams();
  const programmeId = search.get('programmeId');
  const navigate = useNavigate();
  const { data, error, loading } = useApi(() => trackingApi.home(programmeId), [programmeId]);
  const concerned = data ? Object.keys(data.districts).length : 0;
  return (
    <>
      <PageTitle title="NOS PROGRAMMES" breadcrumb={['Accueil', 'Tableau de bord', 'Carte']} />
      {loading && !data && <Loading />}
      {error && <ErrorAlert message={error} />}
      {data && (
        <div className="row">
          <div className="col-lg-7">
            <div className="row">
              {data.programmes.map((p) => (
                <div key={p.id} className="col-md-6">
                  <div className="card" style={{ borderLeft: `4px solid ${p.color ?? '#ccc'}` }}>
                    <div className="card-body">
                      <p className="text-dark mb-0 fw-semibold">
                        <Link to={`/admin/project/programme/${p.id}/subProgrammes`}>{p.title}</Link>
                        {' '}<span className="fw-normal">(<Link to={`/home?programmeId=${p.id}`}>voir la carte</Link>)</span>
                      </p>
                      <h3 className="my-1 font-20 fw-bold">{p.budget.toLocaleString('fr-FR')} MGA</h3>
                      <p className="mb-0 text-truncate text-muted">
                        <span className="text-primary">{(p.avancementMoyen * 100).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %</span>
                        {' '}Achevé · {p.projets} sous-projets
                      </p>
                      <p className="mb-0 text-muted">
                        {p.communes} commune{p.communes > 1 ? 's' : ''} bénéficiaire{p.communes > 1 ? 's' : ''}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="col-lg-5">
            <Card title={`RESULTAT POUR ${data.programmeTitle ?? 'TOUS LES PROGRAMMES'}, ${concerned} DISTRICTS CONCERNES`}
              actions={programmeId && <Link to="/home" className="text-primary">voir tous</Link>}>
              <div className="text-center">
                <MadagascarMap districts={data.districts} names={data.districtNames}
                  onDistrictClick={(code) => navigate(`/admin/project/district/${code}/projects`)} />
              </div>
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
