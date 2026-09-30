# Spec 002: distributing the JDBC driver

- **Status**: implemented (GitHub releases); Maven Central ready, waiting for the owner's account and key
- **Date**: 2026-09-30

## Problem

The driver existed only as a CI artifact, and a user cannot be pointed at that.

## Design

A tag `v*.*.*` runs `.github/workflows/release.yml`:

- **The GitHub release, always.** The version comes from the tag (`versions:set`), the tests run,
  and the release gets `acl-jdbc-<version>-all.jar` and its `.sha256`. This is what a DBeaver user
  downloads.
- **Maven Central, once configured.** `mvn -P central deploy` publishes through the Central Portal
  plugin: the jar (with Arrow's driver and Jackson as dependencies in the pom), the `all` classifier,
  sources and javadoc, all GPG-signed. It needs the namespace `io.github.hugr-lab`, verified through
  the GitHub organization, a Portal token and a signing key. All three are the owner's to create, as
  repository secrets. Without them the step is skipped with a notice, and the release still happens.

On Central, DBeaver resolves the driver by its Maven coordinates and offers updates. That is also the
precondition for listing the driver in DBeaver itself (a PR to its driver definitions), which is a
follow-up.

## Alternatives considered

- **GitHub Packages.** Rejected: even public Maven packages there require a token to download, and
  DBeaver cannot supply one.

## Testing

`mvn -P central -Dgpg.skip package` builds every artifact Central receives. The release workflow
itself is proven by its first tag.
