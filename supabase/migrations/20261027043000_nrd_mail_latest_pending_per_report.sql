-- Keep only the newest pending edition of each store report.
-- Historical and already-published rows are preserved; older pending rows remain archived as expired.
with ranked as (
  select id, row_number() over (
    partition by upper(filename), store_code
    order by received_at desc, id desc
  ) as edition_rank
  from public.nrd_mail_documents
  where status = 'pending'
)
update public.nrd_mail_documents d
set status = 'expired'
from ranked r
where d.id = r.id and r.edition_rank > 1;

create unique index if not exists nrd_mail_one_pending_per_report
  on public.nrd_mail_documents (upper(filename), store_code)
  where status = 'pending';

create or replace function public.nrd_mail_enqueue(document jsonb) returns boolean
language plpgsql security invoker set search_path = public, pg_temp as $$
begin
  perform pg_advisory_xact_lock(842840);

  -- Exact PDF repeats remain idempotent, including already archived/published files.
  if exists(select 1 from public.nrd_mail_documents where id = document->>'id') then
    return false;
  end if;

  -- A newly received edition replaces an older pending copy of that store/report.
  -- Keep the old row as history; do not touch any published documents.
  update public.nrd_mail_documents
  set status = 'expired'
  where status = 'pending'
    and upper(filename) = upper(document->>'filename')
    and store_code = document->>'store_code';

  update public.nrd_mail_documents
  set status = 'expired', pdf_base64 = null
  where status = 'pending'
    and received_at < now() - interval '14 days';

  if (select count(*) from public.nrd_mail_documents where status = 'pending') >= 250 or
     (select coalesce(sum(length(pdf_base64)), 0) from public.nrd_mail_documents) +
       length(document->>'pdf_base64') > 67108864 then
    raise exception 'MAIL_QUEUE_FULL';
  end if;

  insert into public.nrd_mail_documents(id, filename, store_code, pdf_base64)
  values (document->>'id', document->>'filename', document->>'store_code', document->>'pdf_base64');
  return true;
end
$$;
