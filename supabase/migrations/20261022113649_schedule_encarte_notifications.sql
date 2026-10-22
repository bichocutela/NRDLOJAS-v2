-- Provision Vault entries nrd_encartes_scheduler and nrd_encartes_anon_jwt separately.
-- Seed the current source snapshot before enabling this job to avoid replaying old leaflets.
select cron.schedule('nrd-encartes-notifications', '*/5 * * * *', $job$
  select net.http_post(
    url := 'https://kkayksyzksexoarpfxyj.supabase.co/functions/v1/nrd-encartes',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'nrd_encartes_anon_jwt'),
      'x-nrd-encartes-scheduler', (select decrypted_secret from vault.decrypted_secrets where name = 'nrd_encartes_scheduler')
    ), body := '{"mode":"sync"}'::jsonb, timeout_milliseconds := 60000
  );
$job$);
