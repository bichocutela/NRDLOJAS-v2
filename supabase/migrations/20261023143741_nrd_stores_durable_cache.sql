create table public.nrd_stores_cache (id boolean primary key default true check (id), payload jsonb not null, updated_at timestamptz not null default now());
alter table public.nrd_stores_cache enable row level security;
revoke all on public.nrd_stores_cache from public, anon, authenticated;
grant select, insert, update on public.nrd_stores_cache to service_role;
