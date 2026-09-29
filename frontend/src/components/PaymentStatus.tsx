/** Durée d'attente lisible : « aujourd'hui », « 1 jour », « 5 jours ». */
function days(n: number): string {
  if (n <= 0) return "aujourd'hui";
  return n === 1 ? '1 jour' : `${n} jours`;
}

/** Statut d'une facture ; rouge quand le délai de traitement de l'étape en cours est dépassé. */
export function PaymentStatusBadge({ status, enRetard, delaiMax }: { status: unknown; enRetard?: unknown; delaiMax?: unknown }) {
  const s = String(status ?? '');
  if (enRetard === true) {
    return (
      <span className="badge bg-danger" title={`Délai de traitement dépassé${typeof delaiMax === 'number' ? ` (${delaiMax} jours)` : ''}`}>
        {s} · en retard
      </span>
    );
  }
  const tone = s === 'VALIDATE' ? 'success' : s === 'REJECTED' ? 'danger' : 'info';
  return <span className={`badge badge-soft-${tone}`}>{s}</span>;
}

/** Prochain validateur et attente à son étape (« Depuis 2 jours »), en rouge si le délai est dépassé. */
export function WaitingValidator({ name, attenteJours, delaiMax, enRetard }: {
  name: unknown; attenteJours?: unknown; delaiMax?: unknown; enRetard?: unknown;
}) {
  if (!name) return null;
  const late = enRetard === true;
  return (
    <>
      {String(name)}
      {typeof attenteJours === 'number' && (
        <div className={'small ' + (late ? 'text-danger fw-semibold' : 'text-muted')}>
          {attenteJours <= 0 ? "Depuis aujourd'hui" : `Depuis ${days(attenteJours)}`}
          {typeof delaiMax === 'number' && ` (délai ${days(delaiMax)})`}
        </div>
      )}
    </>
  );
}
