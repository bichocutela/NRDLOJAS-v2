# NRD storage optimization

The authorized October 2026 audit measured 923,361,683 bytes in 451 objects.
The largest folders were theme_backgrounds (503 MB) and dynamic-pages (328 MB).

## Existing images

The `Optimize NRD storage losslessly` workflow runs once when its workflow file
is merged, and may later be dispatched manually. It downloads PNG candidates,
encodes lossless WebP, and checks every RGBA pixel, dimensions, and ICC profile.
Animated, high-bit-depth, and oriented images are excluded. A replacement must
save at least 10%. URLs stay unchanged; the response MIME becomes image/webp.
Android's image loader and browsers decode the file content independently of
the existing filename extension. No JSON, video, audio, or PDF is modified.

The workflow archives originals plus `plan.json` as a GitHub Actions artifact
**before** replacing any object. Retention is 30 days. Download this artifact
to restore an original through the Supabase Storage dashboard at its recorded
path with its original MIME type. The function rejects source ETags that changed
after preparation. It cannot delete files. It accepts GitHub OIDC solely from
this repository's main-branch storage-optimizer workflow, with exact repository
and owner IDs; it never exposes service credentials.

The first pass incurs a one-time download of the candidate originals. This
increases current-cycle egress while reducing stored size and future downloads.
It cannot reverse previously consumed egress or guarantee removal of a billing
warning. No recurring batch is scheduled, to avoid repeatedly downloading files.

## New uploads

Android 11+ optimizes suitable PNG uploads through the platform lossless WebP
codec. It retains dimensions and transparency, skips animation and embedded
profiles/orientation metadata, and keeps the original if savings are below 10%.
Older Android versions keep the source. Videos, JPEGs, and configuration files
remain unchanged. The server already uses the uploaded file's MIME type.

## ACP failures

Automatic promotion refresh now backs off after consecutive failures: 1, 2, 4,
8, then at most 15 minutes. A manual refresh bypasses the delay. A successful
refresh clears the failure counter. The last valid snapshot remains available;
normal successful polling retains its existing interval.

Duplicate object ETags suggest about 191 MB of redundant copies, but those
objects are retained until their references in Firebase, dynamic pages, and
configuration are accounted for. Compression frees space without deleting URLs.
