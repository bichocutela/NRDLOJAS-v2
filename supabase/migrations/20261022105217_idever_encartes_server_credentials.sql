-- Tenant credentials are provisioned separately in Vault, never in this migration or APK.
-- The API wrapper is invoker-only; only service_role can execute either function.
create schema if not exists nrd_private;
revoke all on schema nrd_private from public, anon, authenticated;
grant usage on schema nrd_private to service_role;
create or replace function nrd_private.encartes_credentials()
returns jsonb language sql stable security definer set search_path = ''
as $$ select decrypted_secret::jsonb from vault.decrypted_secrets where name = 'nrd_idever_encartes' limit 1 $$;
revoke all on function nrd_private.encartes_credentials() from public, anon, authenticated;
grant execute on function nrd_private.encartes_credentials() to service_role;
create or replace function public.nrd_encartes_credentials()
returns jsonb language sql stable security invoker set search_path = ''
as $$ select nrd_private.encartes_credentials() $$;
revoke all on function public.nrd_encartes_credentials() from public, anon, authenticated;
grant execute on function public.nrd_encartes_credentials() to service_role;
