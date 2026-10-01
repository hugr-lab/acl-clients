# Spec 003: the door's clients - choosing who to sign in as

- **Status**: implemented
- **Date**: 2026-10-01
- **Node side**: duckdb-acl spec 095 (issuers and clients)

## Problem

duckdb-acl 095 split an issuer into the issuer and its clients. The door's discovery no longer names
one `client_id` per issuer; it lists the clients a driver may sign in as, each with the flows it runs:

```json
{"issuers":[{"name":"kc","issuer":"https://kc/realms/x","token_endpoint":"…",
             "device_authorization_endpoint":"…","authorization_endpoint":"…",
             "clients":[{"name":"desktop","client_id":"acl-desktop","flows":["authcode","device"]}]}]}
```

The driver read `issuers[].client_id`, which is gone.

## Design

- `DoorIssuer` carries the issuer's name, URL, endpoints and `clients`; a client without a
  `client_id` is skipped.
- **The issuer**: the connection's `issuer`, matched by URL or by the door's name; else the first
  issuer with a client that runs the flow; else the first with any client.
- **The client**: the connection's `clientId` (its own, as before); else the door's client named by
  the new `client` property (refused, listing the names, when there is none); else the issuer's first
  client that runs the flow; else its first client.
- The flow is `device` for the device sign-in and `authcode` otherwise: the driver's own password
  sign-in runs the IdP's password grant, which needs the same public client the browser sign-in uses.
- The door's `authorization_endpoint` now completes the door-side copy of the endpoints, so the
  browser sign-in works when the IdP's own discovery is not reachable from the client.
- No compatibility with the old document: there are no external users yet.

The dev node (`dev/node.sh`) defines the realm in 095's short form - keys by discovery, one client
with `FLOWS (password, authcode, device)`: the door's password handshake runs as exactly one client.

## Testing

- Unit: the new document parses (a client without an id is skipped); the client is the first that
  runs the flow (two clients, the device flow picks the one that runs it); `client` names one, and a
  wrong name is refused with the names; `issuer` by the door's name; an issuer without clients says
  to set `clientId`.
- e2e (`dev/jdbc-e2e.sh all`) against the dev node and Keycloak: all five live cases; the Python
  example through the door's password handshake.
