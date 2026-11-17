-- Private connector address; never included in client scan status or document responses.
alter table public.nrd_mail_settings add column if not exists connector_url text;
