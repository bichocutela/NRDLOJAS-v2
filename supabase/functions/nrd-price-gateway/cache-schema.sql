create table if not exists public.nrd_promotion_cache (
  key text primary key check (key ~ '^[a-zA-Z0-9_-]{1,160}$'),
  payload jsonb not null default '{}'::jsonb,
  lease bigint not null default 0,
  version bigint not null default 1,
  updated_at timestamptz not null default now()
);
alter table public.nrd_promotion_cache enable row level security;
revoke all on public.nrd_promotion_cache from public, anon, authenticated;
grant select, insert, update, delete on public.nrd_promotion_cache to service_role;
create or replace function public.nrd_cache_write(p_key text, p_payload jsonb, p_lease bigint, p_mode text, p_version bigint default null)
returns setof public.nrd_promotion_cache language plpgsql security invoker set search_path = '' as $$
begin
  if p_mode = 'insert' then
    return query insert into public.nrd_promotion_cache(key,payload,lease) values(p_key,p_payload,p_lease)
      on conflict do nothing returning *;
  elsif p_mode = 'cas' then
    return query update public.nrd_promotion_cache set payload=p_payload,lease=p_lease,
      version=version+1,updated_at=now() where key=p_key and version=p_version returning *;
  elsif p_mode = 'upsert' then
    return query insert into public.nrd_promotion_cache(key,payload,lease) values(p_key,p_payload,p_lease)
      on conflict(key) do update set payload=excluded.payload,lease=excluded.lease,
      version=nrd_promotion_cache.version+1,updated_at=now() returning *;
  else raise exception 'Invalid cache write mode';
  end if;
end;
$$;
revoke all on function public.nrd_cache_write(text,jsonb,bigint,text,bigint) from public,anon,authenticated;
grant execute on function public.nrd_cache_write(text,jsonb,bigint,text,bigint) to service_role;
