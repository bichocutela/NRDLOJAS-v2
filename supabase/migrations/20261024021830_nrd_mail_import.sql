create table public.nrd_mail_settings (
 id boolean primary key default true check(id),
 email text not null,
 key_hash text not null check(key_hash ~ '^[a-f0-9]{64}$')
);
create table public.nrd_mail_documents (
 id text primary key check(id ~ '^[a-f0-9]{64}$'),
 filename text not null check(length(filename)<=160),
 store_code text not null check(store_code ~ '^[0-9]{4}$' and store_code <> '0012'),
 pdf_base64 text check(length(pdf_base64)<=4194304),
 received_at timestamptz not null default now(),
 status text not null default 'pending' check(status in ('pending','published','rejected','expired'))
);
alter table public.nrd_mail_settings enable row level security;
alter table public.nrd_mail_documents enable row level security;
revoke all on public.nrd_mail_settings, public.nrd_mail_documents from public, anon, authenticated;
grant all on public.nrd_mail_settings, public.nrd_mail_documents to service_role;
create index nrd_mail_pending on public.nrd_mail_documents(received_at) where status='pending';
-- Invoker, callable only by the server role; lock bounds simultaneous attachment uploads.
create function public.nrd_mail_enqueue(document jsonb) returns boolean
language plpgsql security invoker set search_path = public, pg_temp as $$
begin
 perform pg_advisory_xact_lock(842840);
 if exists(select 1 from nrd_mail_documents where id=document->>'id') then return false; end if;
 update nrd_mail_documents set status='expired',pdf_base64=null where status='pending' and received_at<now()-interval '14 days';
 if (select count(*) from nrd_mail_documents where status='pending')>=250 or
 (select coalesce(sum(length(pdf_base64)),0) from nrd_mail_documents)+length(document->>'pdf_base64')>67108864 then
 raise exception 'MAIL_QUEUE_FULL'; end if;
 insert into nrd_mail_documents(id,filename,store_code,pdf_base64)
 values(document->>'id',document->>'filename',document->>'store_code',document->>'pdf_base64');
 return true;
end $$;
revoke all on function public.nrd_mail_enqueue(jsonb) from public, anon, authenticated;
grant execute on function public.nrd_mail_enqueue(jsonb) to service_role;
