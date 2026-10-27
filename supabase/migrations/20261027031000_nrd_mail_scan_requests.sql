-- Additive, server-only scan request state. Existing settings, capability and documents stay intact.
alter table public.nrd_mail_settings
  add column if not exists scan_request_id uuid,
  add column if not exists scan_finished_id uuid,
  add column if not exists scan_finished_at timestamptz,
  add column if not exists scan_seen_at timestamptz,
  add column if not exists scan_failed boolean not null default false;
