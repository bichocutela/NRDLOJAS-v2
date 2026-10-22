begin;
create table if not exists public.nrd_encarte_events (
  id bigint primary key check (id > 0),
  payload jsonb not null,
  expires_at timestamptz not null,
  notified_at timestamptz,
  claimed_until timestamptz,
  claim_id uuid,
  created_at timestamptz not null default now()
);
alter table public.nrd_encarte_events enable row level security;
revoke all on public.nrd_encarte_events from public, anon, authenticated;
grant select, insert, update, delete on public.nrd_encarte_events to service_role;

create or replace function public.nrd_claim_encartes(items jsonb, baseline boolean default false)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare result jsonb;
begin
  if jsonb_typeof(items) <> 'array' or jsonb_array_length(items) > 200 then raise exception 'Invalid catalog'; end if;
  perform pg_advisory_xact_lock(734182091);
  insert into public.nrd_encarte_events(id, payload, expires_at, notified_at)
    select (v->>'id')::bigint, v, (v->>'expiresAt')::timestamptz, case when baseline then now() else null end
    from jsonb_array_elements(items) v
    on conflict (id) do update set payload = excluded.payload, expires_at = excluded.expires_at
      where public.nrd_encarte_events.payload is distinct from excluded.payload;
  if baseline then
    update public.nrd_encarte_events set notified_at = now()
    where id in (select (v->>'id')::bigint from jsonb_array_elements(items) v);
  end if;
  -- Keep a small deduplication window; never store PDFs or images here.
  delete from public.nrd_encarte_events where expires_at < now() - interval '90 days';
  with picked as (
    select id from public.nrd_encarte_events
    where notified_at is null and expires_at > now()
      and (claimed_until is null or claimed_until < now())
      and id in (select (v->>'id')::bigint from jsonb_array_elements(items) v)
    order by id limit 20 for update skip locked
  ), claimed as (
    update public.nrd_encarte_events e set claimed_until = now() + interval '5 minutes', claim_id = gen_random_uuid()
    from picked where e.id = picked.id returning e.id, e.payload, e.claim_id
  ) select coalesce(jsonb_agg(to_jsonb(claimed)), '[]'::jsonb) into result from claimed;
  return result;
end $$;
revoke all on function public.nrd_claim_encartes(jsonb, boolean) from public, anon, authenticated;
grant execute on function public.nrd_claim_encartes(jsonb, boolean) to service_role;

create or replace function nrd_private.encartes_credentials()
returns jsonb language sql stable security definer set search_path = ''
as $$ select c.decrypted_secret::jsonb || jsonb_build_object('schedulerKey',
  (select decrypted_secret from vault.decrypted_secrets where name = 'nrd_encartes_scheduler'))
  from vault.decrypted_secrets c where c.name = 'nrd_idever_encartes' limit 1 $$;
revoke all on function nrd_private.encartes_credentials() from public, anon, authenticated;
grant execute on function nrd_private.encartes_credentials() to service_role;
commit;
