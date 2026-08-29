# vinext-starter

A clean full-stack starter running on
[vinext](https://github.com/cloudflare/vinext), with optional Cloudflare D1 and
Drizzle support.

## Prerequisites

- Node.js `>=22.13.0`

## Quick Start

```bash
npm install
npm run dev
npm run build
```

## Backend API boundary

Browser code uses the same-origin `/api` path in hosted builds. The Worker forwards only
that path to the server-side `BACKEND_API_ORIGIN` binding, so production builds must leave
`NEXT_PUBLIC_API_BASE_URL` empty and configure an origin-only HTTPS value such as
`https://api.example.com` through the Sites runtime environment. Values containing a path,
credentials, query, fragment, or an HTTP non-loopback host are rejected. The backend CORS
allowlist must include the deployed Site origin. Hosted requests themselves must also use
HTTPS; plain HTTP is accepted only when both the incoming Site URL and backend are loopback
development addresses. Configure a server-only 32-byte lowercase
hex `BACKEND_PROXY_SHARED_SECRET` generated with `openssl rand -hex 32`; the matching backend
gateway value is `FRONTEND_PROXY_SHARED_SECRET`. Hosted deployments must inject the Worker
value through the platform secret store/runtime secret binding, not a plain build variable.
The placeholder in `.env.example` is deliberately invalid and must never be deployed unchanged.
Never expose either value through a `NEXT_PUBLIC_` variable.

For local direct-browser development, `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` remains
available. Local Worker proxy testing may instead set
`BACKEND_API_ORIGIN=http://localhost:8080`. The proxy never forwards Sites cookies,
`oai-authenticated-user-*`, Cloudflare client metadata, or forwarding headers to Spring.
Instead, it converts the platform-provided client address into a non-reversible HMAC key.
Nginx accepts that key for rate limiting only when the server-only proxy secret matches and
the key is exactly 64 lowercase hexadecimal characters. A request that presents either
internal header without the valid pair is rejected instead of falling back to a shared IP
bucket. Nginx removes both internal headers before forwarding to Spring. If platform client metadata is
missing, the Worker uses one conservative anonymous bucket.

This starter does not use `wrangler.jsonc`.

## Included Shape

- edit site code under `app/`
- `.openai/hosting.json` declares optional Sites D1 and R2 bindings
- `vite.config.ts` simulates declared bindings for local development
- `db/schema.ts` starts intentionally empty
- `examples/d1/` contains an optional D1 example surface
- `drizzle.config.ts` supports local migration generation when needed

## Workspace Auth Headers

Signed-in visitors receive both `oai-authenticated-user-id` and `oai-authenticated-user-email`. Private Sites require every visitor to sign in; public Sites may also have anonymous visitors, for whom neither header is present.

The user ID is stable for the same user on the same Site and different across Sites. Email and name are intended for display or contact purposes.

SIWC-authenticated workspace sites may also receive
`oai-authenticated-user-full-name` when the user's SIWC profile has a non-empty
`name` claim. The full-name value is percent-encoded UTF-8 and is accompanied by
`oai-authenticated-user-full-name-encoding: percent-encoded-utf-8`.

Treat the full name as optional and fall back to email when it is absent:

```tsx
import { headers } from "next/headers";

export default async function Home() {
  const requestHeaders = await headers();
  const userId = requestHeaders.get("oai-authenticated-user-id");
  const email = requestHeaders.get("oai-authenticated-user-email");
  const encodedFullName = requestHeaders.get("oai-authenticated-user-full-name");
  const fullName =
    encodedFullName &&
    requestHeaders.get("oai-authenticated-user-full-name-encoding") ===
      "percent-encoded-utf-8"
      ? decodeURIComponent(encodedFullName)
      : null;

  const displayName = fullName ?? email;
  // ...
}
```

## Optional Dispatch-Owned ChatGPT Sign-In

Import the ready-to-use helpers from `app/chatgpt-auth.ts` when the site needs
optional or required ChatGPT sign-in:

- Use `getChatGPTUser()` for optional signed-in UI.
- Use `requireChatGPTUser(returnTo)` for server-rendered pages that should send
  anonymous visitors through Sign in with ChatGPT.
- Use `chatGPTSignInPath(returnTo)` and `chatGPTSignOutPath(returnTo)` for
  browser links or actions.
- Pass a same-origin relative `returnTo` path for the destination after sign-in
  or sign-out. The helper validates and safely encodes it.
- Mark protected pages with `export const dynamic = "force-dynamic"` because
  they depend on per-request identity headers.

Dispatch owns `/signin-with-chatgpt`, `/signout-with-chatgpt`, `/callback`, the
OAuth cookies, and identity header injection. Do not implement app routes for
those reserved paths. Routes that do not import and call the helper remain
anonymous-compatible.

SIWC establishes identity only; it does not prove workspace membership. Use the
Sites hosting platform's access policy controls for workspace-wide restrictions,
or enforce explicit server-side membership or allowlist checks.

The staff case route uses both controls. `/staff/cases` requires SIWC, and the Worker-only
`/api/internal/staff-capability/{sessionId}` route checks the server-side
`STAFF_ALLOWED_USER_IDS` comma-separated allowlist before using
`DEMO_STAFF_BOOTSTRAP_TOKEN`. Both values are runtime secrets/configuration and must never use
a `NEXT_PUBLIC_` prefix. The bootstrap token is sent only from the Worker to Spring; browser
code receives the short-lived demo staff capability and keeps it in memory. Configure the
hosting access policy as an additional boundary.

Use SIWC for account pages, user-specific dashboards, saved records, and write
actions tied to the current ChatGPT user. Leave public content anonymous.

## Useful Commands

- `npm run dev`: start local development
- `npm run build`: verify the vinext build output
- `npm test`: build the starter and verify its rendered loading skeleton
- `npm run db:generate`: generate Drizzle migrations after schema changes

## Learn More

- [vinext Documentation](https://github.com/cloudflare/vinext)
- [Drizzle D1 Guide](https://orm.drizzle.team/docs/get-started/d1-new)
