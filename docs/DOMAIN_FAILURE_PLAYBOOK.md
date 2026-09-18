# Anime/Drama Domain Failure Playbook

## A. `Not Found`

1. Confirm the exact URL.
2. Normalize trailing slash.
3. Retry once.
4. Record HTTP 404.
5. Mark endpoint `DEGRADED`.
6. Check configured domain override.
7. If a replacement domain is verified, update only `baseUrl`/override.
8. Keep the same stable `sourceId`.

## B. DNS failure

Treat as a hostname problem, not a parser problem.

Do not modify CSS selectors until DNS is healthy.

## C. TLS failure

Do not disable certificate verification. The correct fix is a valid HTTPS endpoint or a legitimate certificate chain.

## D. 403

Do not brute-force, rotate random identities, or bypass an access control. Offer the WebView path when ordinary browser rendering is expected and permitted.

## E. 429

Apply exponential backoff and reduce request frequency. Cache catalog/detail/episode metadata.

## F. 5xx

Retry a bounded number of times. If persistent, mark degraded and continue with another configured source.

## G. HTML changed

Symptoms:

- homepage opens
- HTTP status is 200
- search returns zero cards
- detail title is empty
- episodes are empty

Classify as `SELECTOR_DRIFT`, not `DEAD`.

## H. Player changed

Symptoms:

- detail works
- episodes work
- iframe exists
- resolver returns no media

Classify as `PLAYER_MISSING` or `PLAYER_RESOLVER_FAILURE`.

Fix the player adapter without rewriting catalog/detail parsing.

## I. JavaScript rendering

If HTTP HTML is empty but a normal browser renders content, use the controlled WebView fallback. Do not assume every empty HTTP document means the domain is dead.
