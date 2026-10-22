-- Read-only assertions against the installed parser; never insert into live config.
do $$
begin
  assert nrd_maintenance.manual_offer_end_date('18/10/2026') = date '2026-10-18';
  assert nrd_maintenance.manual_offer_end_date('2026-10-18') = date '2026-10-18';
  assert nrd_maintenance.manual_offer_end_date('31/02/2026') is null;
  assert nrd_maintenance.manual_offer_end_date('') is null;
  assert nrd_maintenance.manual_offer_end_date('not-a-date') is null;
  assert nrd_maintenance.manual_offer_end_date(null) is null;
  -- Full final day is retained; future and unknown dates are not eligible.
  assert nrd_maintenance.manual_offer_end_date('17/10/2026') < date '2026-10-18';
  assert not (nrd_maintenance.manual_offer_end_date('18/10/2026') < date '2026-10-18');
  assert not (nrd_maintenance.manual_offer_end_date('19/10/2026') < date '2026-10-18');
  assert (timestamptz '2026-10-19 02:59:59+00' at time zone 'America/Fortaleza')::date = date '2026-10-18';
  assert (timestamptz '2026-10-19 03:00:00+00' at time zone 'America/Fortaleza')::date = date '2026-10-19';
end;
$$;
