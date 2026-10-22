-- Operational retention only: never remove application rows, files or running jobs.
-- Keep 14 days for diagnostics and cap each cleanup transaction at 10,000 rows.
do $maintenance$
begin
  perform set_config('lock_timeout', '2s', true);
  perform set_config('statement_timeout', '10s', true);
  perform cron.schedule(
    'usage-cron-history-retention',
    '17 4 * * *',
    $job$
      delete from cron.job_run_details
      where runid in (
        select runid from cron.job_run_details
        where end_time < now() - interval '14 days'
        order by runid limit 10000
      );
    $job$
  );
  delete from cron.job_run_details
  where runid in (
    select runid from cron.job_run_details
    where end_time < now() - interval '14 days'
    order by runid limit 10000
  );
end;
$maintenance$;
