# ACP provider controls

The Master Panel stores provider availability in the existing Supabase control
document `config/acpProvider`. No Firebase catalog or promotions schema is changed.
Only the verified Master can write this document through `nrd-promotion-control`.

The document contains `legacyEnabled`, `acpTechEnabled`, and `primary`. When one
provider is enabled, requests use it. When both are enabled, the gateway tries the
selected primary and uses the other only if that provider fails. A successful
response, including an empty search result, is final; results from the two sources
are never combined.

Promotion snapshots are read entirely from one provider. If that provider fails
during a snapshot, the gateway discards that attempt and restarts the complete read
with the secondary provider, so a snapshot cannot mix campaigns or products from
two sources. The provider configuration is cached in each edge instance for up to
10 seconds.

ACP credentials remain Supabase Edge Function secrets:

- Legacy ACP: `NRD_PRICE_LOGIN`, `NRD_PRICE_PASSWORD`
- ACP Tech: `ACPTECH_LOGIN`, `ACPTECH_PASSWORD`

Changing a provider password requires updating its Supabase secret. If both
providers are enabled, the other provider can keep consultations working while the
credential is corrected. Passwords are never stored in Firebase, the control
document, or the Android app.

With no saved control document, `ACP_PROVIDER` continues to select the initial
provider, preserving the deployment's current behavior.
