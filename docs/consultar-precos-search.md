# Consultar Preços

Description searches read all pages of the filtered term (250 upstream products
per request), apply all words, `#` segments, and normalized measures, and sort
the full matching set by accent-insensitive description, code, barcode, and ID.
Only then is the list split into local pages of 20. Changing pages reuses that
search snapshot. Explicit refresh rebuilds it. The unfiltered catalog is never
walked; more than 10,000 candidates requires a more specific term.

Incomplete pages, duplicate IDs, changed totals, or a provider change interrupt
the scan instead of publishing an incomplete/mixed result. The gateway labels
product and campaign responses with their actual provider. Campaign prices are
used only for products from that same provider.

Typing debounces for 300 ms, cancels previous work immediately, and loads campaign
conditions for the result list. The list remains clickable while supplemental
conditions load. Campaign errors are visible and keep the product's registered
price available. Late responses cannot replace a newer query.

List and detail use the same offer selection: honor inactive campaigns, start/end
timestamps, API dates and manual fallback dates; retain the latest active condition
per offer family. An active promotional price is not preceded by a separate normal
price. When a dated De/Por ends, the documented regular reference is shown.

Regression coverage includes globally ordered multi-page results, page reuse,
`#` matches present only on later source pages, provider changes, exact start/end
times, expired/future campaigns, and restoration of the normal price.
