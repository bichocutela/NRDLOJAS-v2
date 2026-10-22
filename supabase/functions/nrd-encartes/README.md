# Encartes de Natal

`GET /functions/v1/nrd-encartes` accepts the app's Supabase legacy anon JWT through
platform JWT verification. It exposes only public leaflet metadata. It never accepts
an arbitrary upstream path or URL, forwards a tenant token, or proxies PDF/image bytes.

Idever tenant OAuth configuration lives in Vault under `nrd_idever_encartes`
(JSON: `tenantName`, `clientId`, `clientSecret`). Provision/rotate it separately with
Vault's dashboard or `vault.create_secret` / `vault.update_secret`; never commit values.
The migration creates a fixed-secret lookup callable only by `service_role`.

Metadata is fetched from `/api/v1/tabloids` and cached for five minutes per isolate;
concurrent refreshes share one request. The entire source snapshot is replaced at each
successful refresh, so deleted IDs disappear and new IDs appear. No merged/stale catalog
is retained after a failed refresh. A source outage yields 503 and the app offers retry.
The app refreshes on opening/resuming and every minute while visible. The effective
source delay is up to five minutes plus one polling interval/network latency.

Only editions available to a known Natal metropolitan store are included. Mossoró-only
editions are excluded; a nationwide/shared edition remains eligible for Natal.
Names alone are never used to deduplicate; the source ID identifies an edition.
Invalid, future and expired entries are excluded. RN timestamps are UTC-03:00.
The Android client also checks validity so a warm cache cannot show an expired leaflet.

Public asset URLs must use HTTPS and `datalakeidever.blob.core.windows.net`. Covers and
PDFs load directly from that CDN, avoiding Supabase Storage and media egress. The PDF
viewer uses the app's existing cache/validation and reader. Supabase credentials and
the tenant OAuth secret never ship in the Android app (only its public anon key).

Verification: `node --test supabase/functions/nrd-encartes/*.test.mjs` and Android CI.

## Opt-in notifications

The list's bell offers a single persisted mode: off, simple, or detailed. Both active
styles use the same `encartes_natal` FCM topic; the Android receiver chooses exactly one
message format and deduplicates by the source ID. Detailed messages include the full
name and validity; simple messages only announce availability. Tapping opens Encartes.
Android permission and general NRD notification preferences are respected.

The protected POST handler accepts only `sync`, `seed`, or `probe`; no caller may supply
message content, a recipient, or an upstream URL. It requires platform JWT verification
and a separate random scheduler capability from Vault. The capability is never in the
APK or Git. `probe` uses FCM validate-only and sends no actual notification.

Before scheduling, run `seed` once to mark existing active source IDs as known. The job
checks the source every five minutes, regardless of whether the app is open. The private
service-role-only outbox claims new IDs with an atomic SQL lease, acknowledges after
successful FCM delivery and releases failures for retry. Withdrawn, future and expired
source items are never queued for delivery. Names/validity edits to the same ID do not
create another notification. Android deduplication handles retries after a lost server
acknowledgement. Old event rows are removed after 90 days; no media bytes are stored.

Vault setup: `nrd_encartes_scheduler` is a random 32-byte secret; `nrd_encartes_anon_jwt`
is the project's public legacy anon JWT for cron platform authentication. Firebase
credentials use the existing server-only `FIREBASE_SERVICE_ACCOUNT` environment variable.
The notification channel is `encartes`. Delivery also depends on Android/FCM connectivity,
permissions and battery restrictions; the five-minute scheduler is not a delivery SLA.
