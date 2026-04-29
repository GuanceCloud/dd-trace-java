# Redis Split-by-Host PR Draft

## Title

Fix Redis split-by-host service naming for Redisson and Lettuce cluster clients

## PR Body

### Summary

This PR fixes Redis `split-by-host` behavior for Redisson and Lettuce cluster clients.

We found that `DD_TRACE_DB_CLIENT_SPLIT_BY_HOST=true` was configured correctly, but Redis spans could still fail to rename their service based on the target host in two important cases:

1. Redisson spans never went through the database decorator path that applies `trace.db.client.split-by-host`.
2. Lettuce cluster spans could miss host information because they did not always have the `RedisURI` context used by the single-node connection path.

As a result, `split-by-host` could work in single-node mode but fail in cluster mode, and some Redis spans could show an empty `db_host`, especially around script-related commands such as `EVALSHA`.

### Root Cause

#### Redisson

Redisson command spans were only using peer connection tagging and were not invoking the connection flow in `DatabaseClientDecorator` that applies service renaming for `split-by-host`.

That meant:

- `peer.hostname` was not guaranteed to be populated consistently
- the span service name could never be updated from the actual target host

#### Lettuce cluster

Lettuce command spans relied on `StatefulConnection -> RedisURI` context populated by the single-node `RedisClient` connection path.

In cluster mode, command writes do not always follow that path, so spans could finish without usable host metadata and `split-by-host` would not be applied.

### Changes

#### Redisson

Updated the Redisson instrumentations for:

- `redisson-2.0.0`
- `redisson-2.3.0`
- `redisson-3.10.3`

Changes:

- added an `InetSocketAddress`-aware connection helper
- read the host with `getHostString()`
- preserved peer connection tagging
- populated `peer.hostname` explicitly
- updated the span service name when `trace.db.client.split-by-host` is enabled

#### Lettuce 5

Updated the Lettuce 5 instrumentation to derive the Redis target from the live Netty channel in `DefaultEndpoint.write`.

Changes:

- read the actual remote socket address from the channel
- populate `peer.hostname`
- populate `peer.port`
- update the span service name when `trace.db.client.split-by-host` is enabled

### Documentation

This PR also adds two documents:

- `docs/redis-split-by-host-analysis.md`
- `docs/redis-split-by-host-report.md`

The report document includes operational guidance for MySQL and Redis naming.

### Operational Guidance

`split-by-host` only reflects the host name actually seen by the client. It does not translate generic service names like `mysql` or `redis` into business-friendly aliases by itself.

If users want APM to show names such as:

- `mysql-1`
- `mysql-order-cluster`
- `redis-order-1`
- `redis-member-cluster-a`

then those aliases must already be used in:

- application connection settings
- DNS / CNAME
- service discovery results

This is especially important for MySQL clusters. If the expected APM service name is `mysql-1`, the application should connect to a host alias such as `mysql-1` or a stable hostname derived from it, rather than a raw IP or an unstable node name.

### Validation

Validated with:

```bash
./gradlew :dd-java-agent:instrumentation:lettuce:lettuce-5.0:compileJava :dd-java-agent:instrumentation:redisson:redisson-3.10.3:compileJava
```

Additional Redisson module compilation was also completed earlier during the investigation.

### Expected Outcome

After this change:

- Redisson spans can participate in `split-by-host`
- Lettuce cluster spans can derive service names from the actual Redis node they talk to
- Redis spans should carry host metadata more consistently
- empty Redis host fields should be significantly reduced

