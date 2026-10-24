-- Subscription preferences stay server-only under the table's existing RLS and grants.
alter table public.web_push_subscriptions add column if not exists preferences jsonb not null default '{}'::jsonb;
