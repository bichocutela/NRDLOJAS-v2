create table if not exists public.nrd_control_documents (
 path text primary key,
 fields jsonb not null default '{}'::jsonb,
 version bigint not null default 1,
 deleted boolean not null default false,
 updated_at timestamptz not null default now()
);
alter table public.nrd_control_documents enable row level security;
revoke all on public.nrd_control_documents from public, anon, authenticated;
grant select,insert,update,delete on public.nrd_control_documents to service_role;
create or replace function public.nrd_control_write(p_path text,p_fields jsonb,p_merge boolean default false,p_version bigint default null,p_insert_only boolean default false,p_deleted boolean default false)
returns setof public.nrd_control_documents language plpgsql security invoker set search_path='' as $$
begin
 if p_version is not null then
  return query update public.nrd_control_documents set fields=case when p_merge then fields||p_fields else p_fields end,
    version=version+1,deleted=p_deleted,updated_at=now() where path=p_path and version=p_version returning *;
 elsif p_insert_only then
  return query insert into public.nrd_control_documents(path,fields,deleted) values(p_path,p_fields,p_deleted) on conflict do nothing returning *;
 else
  return query insert into public.nrd_control_documents(path,fields,deleted) values(p_path,p_fields,p_deleted)
  on conflict(path) do update set fields=case when p_merge then nrd_control_documents.fields||excluded.fields else excluded.fields end,
    version=nrd_control_documents.version+1,deleted=excluded.deleted,updated_at=now() returning *;
 end if;
end;$$;
revoke all on function public.nrd_control_write(text,jsonb,boolean,bigint,boolean,boolean) from public,anon,authenticated;
grant execute on function public.nrd_control_write(text,jsonb,boolean,bigint,boolean,boolean) to service_role;
