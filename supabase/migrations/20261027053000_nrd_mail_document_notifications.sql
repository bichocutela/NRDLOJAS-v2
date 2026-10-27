-- Alert at most once per scan burst, regardless of how many store PDFs arrive together.
alter table public.nrd_mail_settings
  add column if not exists mail_notification_sent_at timestamptz;

create or replace function public.nrd_mail_claim_notification()
returns boolean
language plpgsql
security invoker
set search_path = public
as $$
declare
  updated_rows integer;
begin
  perform pg_advisory_xact_lock(hashtext('nrd_mail_notification'));
  update public.nrd_mail_settings
     set mail_notification_sent_at = now()
   where id = true
     and (
       mail_notification_sent_at is null
       or mail_notification_sent_at < now() - interval '15 minutes'
     );
  get diagnostics updated_rows = row_count;
  return updated_rows > 0;
end;
$$;

revoke all on function public.nrd_mail_claim_notification() from public, anon, authenticated;
grant execute on function public.nrd_mail_claim_notification() to service_role;
