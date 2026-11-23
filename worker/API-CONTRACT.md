# 7TV contract used by Moonotes 0.5.3

Verified on 2026-11-23 using read-only HTTP requests.

1. `GET https://7tv.io/v3/users?query=v0kky` returned **404**.
2. `POST https://7tv.io/v3/gql` with `FindChannel` below returned **200** and
   one exact Twitch connection for `v0kky` (`257831564`). Other fuzzy search
   matches were discarded; matching a 7TV display name alone is insufficient.
3. `GET https://7tv.io/v3/users/twitch/257831564` returned **200** with the
   current active set `01KYCQS3B86R95R13AAWJSBVT7`. It contained 705 names and
   704 unique image IDs at the time of the check. These are live counts,
   not a fixed expected count in any regression test.

```graphql
query FindChannel($name: String!) {
  users(query: $name, limit: 20) {
    connections { id platform username }
  }
}
```

The connected-account response must identify platform `TWITCH`, the resolved
numeric ID and the requested login. Its `emote_set` is used directly. A saved
set ID is not treated as authoritative on refresh because channel owners can
change their active set. A real empty set is allowed; null/missing set is not
silently converted to an empty valid catalogue.

## Primary sources

- https://github.com/SevenTV/SevenTV/blob/main/apps/api/src/http/v3/gql/queries/user/mod.rs
  (`users` searches Twitch connection names and prioritizes exact matches).
- https://github.com/SevenTV/SevenTV/blob/main/apps/api/src/http/v3/rest/users.rs
  (current REST routes include user ID and platform/ID; the old `/users?query`
  route is absent; the connection response resolves the active set).

The same read-only chain was also verified for `xqc` (969 names / 966 image IDs)
and `ohnepixel` (946 / 946). The actual new Worker module's optional Node.js
`check-live.mjs v0kky` command returned 705 / 704 successfully, without R2 access. The new Java `CatalogTransport` was also compiled with JDK 25
and completed the same v0kky lookup using its real bounded HTTP client. The
workspace proxy/trust configuration was supplied for that one verification run;
no proxy endpoint or credentials were added to the distributed mod.

## Verification boundaries

`npm test` uses deterministic mocked fetch/R2 and tests limits, routing, cache
compatibility and failure handling. `npm run check:live -- <login>` is a separate
read-only network check. This package has not deployed anything to the owner's
Cloudflare account. A successful 7TV lookup does not prove that `workers.dev`
is reachable from every ISP or that an older live Worker has been upgraded.

## Canonical image URL regression (0.5.3)

The failing screenshot ID was `01f5z4b9700008kdxa2tjqbngw`, named
`TEATIMEMEGAZOOMAAAAAA`. Read-only requests with redirects disabled on 2026-11-23:

| Requested CDN ID (`/emote/<id>/2x.webp`) | Status | Result |
| --- | --- | --- |
| `01f5z4b9700008kdxa2tjqbngw` | 308 | Relative Location with uppercase ULID |
| `01F5Z4B9700008KDXA2TJQBNGW` | 200 | WebP, 30,588 bytes |
| `01f6mzgcng000255k4x1k7nthr` | 308 | Relative Location with uppercase ULID |
| `01F6MZGCNG000255K4X1K7NTHR` | 200 | WebP, 252,100 bytes |
| `60a304efac2bcb20ef20fa89` | 308 | `01F5YCPHCR000ARAYB43QJ1YM9` |
| `60b9e0e9b55f4fa0ed9e2785` | 308 | `01F7B1D3H8000BAQTFM3PSW9W5` |

Worker 0.5.2 constructed lowercase URLs and used `redirect: 'error'`; its catch
turned this legitimate CDN redirect into `upstream_unreachable`/502. Existing
R2 objects took the cache path and hid this defect. Version 0.5.3 creates the
canonical URL before requesting the CDN and never follows Location headers.
Cache keys intentionally remain the same lowercase IDs.

The published 7TV implementation was read at commit
`5606ada34724324875f6961eebad51e1e3d9044b`:

- https://github.com/SevenTV/SevenTV/blob/5606ada34724324875f6961eebad51e1e3d9044b/apps/cdn/src/http/cdn.rs
  compares the request path with the canonical cache key and redirects mismatches.
- https://github.com/SevenTV/SevenTV/blob/5606ada34724324875f6961eebad51e1e3d9044b/shared/src/database/id.rs
  `from_object_id` uses the 4-byte timestamp multiplied by 1000 and the remaining
  8 bytes as the random component of a ULID. The network client and Worker use
  this mapping only for outbound CDN URLs; they do not rename caches.

The actual exported `fetchCdnImage` function in this package successfully read
the failing emote's canonical WebP with SHA-256
`ef9af2a20ad281397ad7e37f9e493a7e42fb57f6b4189a4b486d4a80257a02b0`.
The actual Java 25 `ImageDownloader` and `BoundedHttp` path independently read
the same 30,588-byte image with the same hash, and the legacy ObjectId example
with 18,146 bytes / SHA-256
`0d2d3757fd9607295153b1b1ab9f844181e4d4b75e87d475938f5e2cb095e3e4`.
Only the verification process received this workspace's proxy and trust-store
configuration; neither setting was copied into the distributed client.
The owner's deployed Worker returned Cloudflare 403/1010 to this workspace, so
its deployment/version cannot be verified from that network observation.
