-- Run as database owner. Maintenance is private and never exposed as a client RPC.
create schema if not exists nrd_maintenance;
revoke all on schema nrd_maintenance from public, anon, authenticated;

create or replace function nrd_maintenance.manual_offer_end_date(raw text)
returns date language plpgsql immutable strict security invoker
set search_path = '' as $$
declare normalized text;
begin
  if raw ~ '^\d{2}/\d{2}/\d{4}$' then
    normalized := substring(raw, 7, 4) || '-' || substring(raw, 4, 2) || '-' || substring(raw, 1, 2);
  elsif raw ~ '^\d{4}-\d{2}-\d{2}$' then
    normalized := raw;
  else
    return null;
  end if;
  return normalized::date;
exception when invalid_datetime_format or datetime_field_overflow then
  return null;
end;
$$;
revoke all on function nrd_maintenance.manual_offer_end_date(text) from public, anon, authenticated;

create or replace function nrd_maintenance.prune_expired_manual_offer_validity()
returns integer language plpgsql security invoker set search_path = '' as $$
declare document_fields jsonb; expired_keys text[];
begin
  -- Serialize with nrd_control_write; preserve concurrent saves and its CAS version.
  select fields into document_fields from public.nrd_control_documents
    where path = 'config/acpOfferValidity' and not deleted for update;
  if not found then return 0; end if;
  select array_agg(entry.key) into expired_keys
    from jsonb_each(document_fields) as entry
    where nrd_maintenance.manual_offer_end_date(
      entry.value #>> '{mapValue,fields,endDate,stringValue}'
    ) < (current_timestamp at time zone 'America/Fortaleza')::date;
  if expired_keys is null then return 0; end if;
  update public.nrd_control_documents
    set fields = document_fields - expired_keys, version = version + 1, updated_at = now()
    where path = 'config/acpOfferValidity';
  return cardinality(expired_keys);
end;
$$;
revoke all on function nrd_maintenance.prune_expired_manual_offer_validity() from public, anon, authenticated;

-- pg_cron uses UTC: 03:01 UTC = 00:01 America/Fortaleza.
select cron.schedule('nrd-expired-manual-offer-validity', '1 3 * * *',
  'select nrd_maintenance.prune_expired_manual_offer_validity();');
