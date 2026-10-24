create table if not exists public.nrd_integration_login_versions (
  provider text primary key check (provider in ('offers','legacy','acptech')),
  revision bigint not null default 0 check (revision >= 0),
  updated_at timestamptz,
  updated_by text
);
alter table public.nrd_integration_login_versions enable row level security;
revoke all on public.nrd_integration_login_versions from public, anon, authenticated;
grant select, insert, update on public.nrd_integration_login_versions to service_role;
insert into public.nrd_integration_login_versions(provider) values ('offers'),('legacy'),('acptech')
on conflict do nothing;

create or replace function nrd_private.integration_login(p_provider text)
returns jsonb language plpgsql stable security definer set search_path = ''
as $$
declare secret_name text; value jsonb; changed_at timestamptz; version bigint;
begin
  if coalesce(current_setting('request.jwt.claims',true),'{}')::jsonb->>'role' is distinct from 'service_role'
    then raise exception 'SERVER_ONLY' using errcode='42501'; end if;
  secret_name := case p_provider when 'offers' then 'nrd_portal_account'
    when 'legacy' then 'nrd_acp_legacy_account' when 'acptech' then 'nrd_acptech_account' else null end;
  if secret_name is null then raise exception 'INVALID_PROVIDER'; end if;
  select decrypted_secret::jsonb,updated_at into value,changed_at from vault.decrypted_secrets
    where name=secret_name limit 1;
  select revision into version from public.nrd_integration_login_versions where provider=p_provider;
  return jsonb_build_object('login',coalesce(value->>'login',value->>'user'),
    'password',value->>'password','revision',coalesce(version,0),'updatedAt',changed_at);
end;
$$;
revoke all on function nrd_private.integration_login(text) from public, anon, authenticated;
grant execute on function nrd_private.integration_login(text) to service_role;

create or replace function public.nrd_integration_login(p_provider text)
returns jsonb language sql stable security invoker set search_path = ''
as $$ select nrd_private.integration_login(p_provider) $$;
revoke all on function public.nrd_integration_login(text) from public, anon, authenticated;
grant execute on function public.nrd_integration_login(text) to service_role;

create or replace function nrd_private.set_integration_login(
  p_provider text,p_login text,p_password text,p_actor text,p_revision bigint
) returns jsonb language plpgsql volatile security definer set search_path = ''
as $$
declare secret_name text; secret_id uuid; version bigint; changed_at timestamptz := clock_timestamp();
  active_until timestamptz; recovery_store bigint; previous_login text;
begin
  if coalesce(current_setting('request.jwt.claims',true),'{}')::jsonb->>'role' is distinct from 'service_role'
    then raise exception 'SERVER_ONLY' using errcode='42501'; end if;
  secret_name := case p_provider when 'offers' then 'nrd_portal_account'
    when 'legacy' then 'nrd_acp_legacy_account' when 'acptech' then 'nrd_acptech_account' else null end;
  if secret_name is null or coalesce(length(trim(p_login)),0) not between 1 and 256
    or coalesce(length(p_password),0) not between 1 and 512 or coalesce(p_actor,'') !~ '^[A-Za-z0-9_-]{1,128}$'
    or p_revision is null then raise exception 'INVALID_REQUEST'; end if;
  select revision into version from public.nrd_integration_login_versions
    where provider=p_provider for update;
  if version is distinct from p_revision then raise exception 'CREDENTIAL_CONFLICT'; end if;
  if p_provider = 'offers' then
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('nrd_portal_credential_rotation',0));
    select until_at,original_store_id into active_until,recovery_store from nrd_private.portal_offer_lock where singleton for update;
    if active_until > clock_timestamp() then raise exception 'LOGIN_IN_USE'; end if;
    if recovery_store is not null then
      select coalesce(decrypted_secret::jsonb->>'login',decrypted_secret::jsonb->>'user') into previous_login
        from vault.decrypted_secrets where name=secret_name limit 1;
      if previous_login is distinct from trim(p_login) then raise exception 'LOGIN_RECOVERY_PENDING'; end if;
    end if;
  end if;
  select id into secret_id from vault.secrets where name=secret_name limit 1;
  if secret_id is null then
    perform vault.create_secret(jsonb_build_object('login',trim(p_login),'user',trim(p_login),'password',p_password)::text,
      secret_name,'NRD integration login; server-only; managed by authenticated Mestre');
  else
    perform vault.update_secret(secret_id,jsonb_build_object('login',trim(p_login),'user',trim(p_login),'password',p_password)::text);
  end if;
  update public.nrd_integration_login_versions set revision=version+1,updated_at=changed_at,updated_by=p_actor
    where provider=p_provider;
  return jsonb_build_object('revision',version+1,'updatedAt',changed_at);
end;
$$;
revoke all on function nrd_private.set_integration_login(text,text,text,text,bigint) from public, anon, authenticated;
grant execute on function nrd_private.set_integration_login(text,text,text,text,bigint) to service_role;

create or replace function public.nrd_set_integration_login(
  p_provider text,p_login text,p_password text,p_actor text,p_revision bigint
) returns jsonb language sql volatile security invoker set search_path = ''
as $$ select nrd_private.set_integration_login(p_provider,p_login,p_password,p_actor,p_revision) $$;
revoke all on function public.nrd_set_integration_login(text,text,text,text,bigint) from public, anon, authenticated;
grant execute on function public.nrd_set_integration_login(text,text,text,text,bigint) to service_role;

-- Serialize account rotation with acquisition of the temporary store-selection lease.
create or replace function public.nrd_portal_offer_lock(
  p_owner uuid, p_operation text, p_original bigint default null, p_selected bigint default null
) returns jsonb language plpgsql volatile security invoker set search_path = ''
as $$
declare state nrd_private.portal_offer_lock%rowtype;
begin
  if p_operation = 'acquire' then
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('nrd_portal_credential_rotation',0));
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
