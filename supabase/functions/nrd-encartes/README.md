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
