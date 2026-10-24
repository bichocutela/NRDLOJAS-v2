-- Personal credentials are provisioned separately in Vault, never in migrations.
create schema if not exists nrd_private;
revoke all on schema nrd_private from public, anon, authenticated;
grant usage on schema nrd_private to service_role;

create or replace function nrd_private.portal_account()
returns jsonb language plpgsql stable security definer set search_path = ''
as $$
begin
  if coalesce(current_setting('request.jwt.claims', true), '{}')::jsonb ->> 'role'
      is distinct from 'service_role' then
    raise exception 'SERVER_ONLY' using errcode = '42501';
  end if;
  return (select decrypted_secret::jsonb from vault.decrypted_secrets
          where name = 'nrd_portal_account' limit 1);
end;
$$;
revoke all on function nrd_private.portal_account() from public, anon, authenticated;
grant execute on function nrd_private.portal_account() to service_role;

create or replace function public.nrd_portal_account()
returns jsonb language sql stable security invoker set search_path = ''
as $$ select nrd_private.portal_account() $$;
revoke all on function public.nrd_portal_account() from public, anon, authenticated;
grant execute on function public.nrd_portal_account() to service_role;
