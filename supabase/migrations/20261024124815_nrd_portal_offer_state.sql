-- Shared snapshots contain normalized commercial data only, never account data.
create table if not exists public.nrd_store_offer_cache (
  store_id bigint primary key check (store_id > 0),
  payload jsonb not null check (jsonb_typeof(payload->'offers') = 'array'
    and (payload->>'storeId')::bigint = store_id),
  fetched_at timestamptz not null default now()
);
alter table public.nrd_store_offer_cache enable row level security;
revoke all on public.nrd_store_offer_cache from public, anon, authenticated;
grant select, insert, update, delete on public.nrd_store_offer_cache to service_role;

create table if not exists nrd_private.portal_offer_lock (
  singleton boolean primary key default true check (singleton),
  owner uuid not null,
  until_at timestamptz not null,
  original_store_id bigint,
  selected_store_id bigint
);
alter table nrd_private.portal_offer_lock enable row level security;
revoke all on nrd_private.portal_offer_lock from public, anon, authenticated;
grant select, insert, update on nrd_private.portal_offer_lock to service_role;

-- A lease outlives the function wall-time limit. The recovery context is written
-- before changing the official account and survives a killed worker.
create or replace function public.nrd_portal_offer_lock(
  p_owner uuid, p_operation text, p_original bigint default null, p_selected bigint default null
) returns jsonb language plpgsql volatile security invoker set search_path = ''
as $$
declare state nrd_private.portal_offer_lock%rowtype;
begin
  if p_operation = 'acquire' then
    insert into nrd_private.portal_offer_lock(singleton,owner,until_at)
    values (true,p_owner,clock_timestamp()+interval '10 minutes')
    on conflict (singleton) do update
      set owner=excluded.owner, until_at=excluded.until_at
      where nrd_private.portal_offer_lock.until_at <= clock_timestamp()
         or nrd_private.portal_offer_lock.owner = p_owner;
  end if;
  select * into state from nrd_private.portal_offer_lock where singleton for update;
  if state.owner is distinct from p_owner or state.until_at <= clock_timestamp() then
    return jsonb_build_object('acquired',false);
  end if;
  if p_operation = 'mark' then
    if p_original is null or p_original <= 0 or p_selected is null or p_selected <= 0 then
      raise exception 'INVALID_STORE';
    end if;
    update nrd_private.portal_offer_lock set original_store_id=p_original,selected_store_id=p_selected
    where singleton;
  elsif p_operation = 'clear' then
    update nrd_private.portal_offer_lock set original_store_id=null,selected_store_id=null where singleton;
  elsif p_operation = 'release' then
    update nrd_private.portal_offer_lock set until_at=clock_timestamp() where singleton;
  elsif p_operation not in ('acquire','check') then
    raise exception 'INVALID_OPERATION';
  end if;
  return jsonb_build_object('acquired',true,'originalStoreId',state.original_store_id,
                           'selectedStoreId',state.selected_store_id);
end;
$$;
revoke all on function public.nrd_portal_offer_lock(uuid,text,bigint,bigint) from public, anon, authenticated;
grant execute on function public.nrd_portal_offer_lock(uuid,text,bigint,bigint) to service_role;
