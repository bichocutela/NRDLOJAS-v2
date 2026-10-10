-- Capability stays encrypted in Vault. Only a SHA-256 digest is readable by the Edge Function.
do $$
declare capability text;
begin
 select decrypted_secret into capability from vault.decrypted_secrets where name='nrd_promotion_scheduler_token';
 if capability is null then
  capability=encode(extensions.gen_random_bytes(32),'hex');
  perform vault.create_secret(capability,'nrd_promotion_scheduler_token');
 end if;
 perform public.nrd_cache_write('promotion_scheduler',jsonb_build_object('hash',encode(extensions.digest(capability,'sha256'),'hex')),0,'upsert',null);
end;$$;
-- Enable only after the new gateway is deployed and migration has completed.
select cron.schedule('nrd-promotion-sync','* * * * *',$job$
 select net.http_post(
  url:='https://kkayksyzksexoarpfxyj.supabase.co/functions/v1/nrd-price-gateway',
  headers:=jsonb_build_object('Content-Type','application/json','x-nrd-scheduler-token',
    (select decrypted_secret from vault.decrypted_secrets where name='nrd_promotion_scheduler_token')),
  body:='{"operation":"promotion_tick"}'::jsonb,timeout_milliseconds:=10000
 );
$job$);

-- Retry the one-time source copy after quota recovery; no source reads after ready.
select cron.schedule('nrd-promotion-control-import','*/5 * * * *',$job$
 select net.http_post(
  url:='https://kkayksyzksexoarpfxyj.supabase.co/functions/v1/nrd-control-bootstrap',
  headers:=jsonb_build_object('Content-Type','application/json','x-nrd-scheduler-token',
    (select decrypted_secret from vault.decrypted_secrets where name='nrd_promotion_scheduler_token')),
  body:='{}'::jsonb,timeout_milliseconds:=120000
 ) where not coalesce((select (payload->>'ready')::boolean from public.nrd_promotion_cache where key='promotion_control_migration'),false);
$job$);
