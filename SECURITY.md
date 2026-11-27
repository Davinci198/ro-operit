# Security policy and repository decisions

This file records the repository-wide security decisions that a scanner or a reviewer would
otherwise have to re-derive. Keep it current when you change the related workflows or tooling.

## GitHub Actions are pinned to full commit SHAs

Every `uses:` reference in `.github/workflows/` is pinned to a 40-character commit SHA with the
human-readable version in a trailing comment, for example:

```yaml
uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4
```

Tags and branch names are mutable: an action owner, or anyone who compromises that account, can
repoint `@v4` at different code without any change in this repository. Pinning to a SHA makes the
reviewed commit the one that runs, and it is what the `[policy] Pin GitHub Actions to full commit
SHAs` finding asks for.

When you bump an action:

1. Resolve the tag to its commit SHA (`git ls-remote` or the GitHub API).
2. Replace the SHA and update the `# vN` comment.
3. Do not reintroduce a tag or a branch name such as `@main`.

## Helper scripts may invoke trusted system tools without a shell

Scripts under `ci/script/` and `tools/` call system tools (`git`, `python3`, `gradle`, `aapt2`)
through `subprocess.run([...])` with an argument list. No script uses `shell=True`, and that is
deliberate:

- With an argument list there is no shell to interpret `;`, `|`, `$()`, or globs, so a value that
  came from a PR branch, a workflow input, or a filename cannot become a command.
- Quoting rules differ across shells. Passing a list removes that class of bug entirely.

Do not add `shell=True`, and do not build a command by joining strings and passing the result to a
shell. For the rare case where shell syntax is genuinely required, invoke `sh -c` with static text
and pass the variable parts as positional parameters:

```python
subprocess.run(["sh", "-c", "some-tool --flag \"$1\"", "sh", user_value], check=True)
```

## Permissions and credentials in workflows

- Workflow jobs declare explicit minimal `permissions:`; the default token is not left at
  repository defaults.
- `actions/checkout` uses `persist-credentials: false` in jobs that build and upload artifacts.
- The privileged release workflow (`release-clone.yml`) scopes its caches per commit instead of
  restoring broad keys, so it never consumes cache entries written by untrusted runs.

## XML parsing

Python tooling parses XML through `_safe_xml_parse` / `_safe_xml_fromstring`, which reject DTD and
entity declarations. Use those helpers instead of calling `xml.etree.ElementTree.parse` or
`fromstring` directly: the stdlib parser resolves internal entities, which allows entity-expansion
(billion laughs) attacks, and older versions also honour external entities.

## Model-authored HTML in the web chat

Content rendered from an `<html>` block goes through `sanitizeChatHtml` in
`web-chat/src/ui/features/chat/util/sanitizeChatHtml.ts`, which rebuilds it from an allowlist of tags
and attributes. Its only caller is
`web-chat/src/ui/features/chat/components/part/CustomXmlRenderer.tsx`. Never pass chat content to
`dangerouslySetInnerHTML` without sanitizing it first: the content is attacker-influenced whenever a
prompt, a tool result, or a scraped page reaches it.

## The workspace file server is loopback-only and token-authenticated

`LocalWebServer` (NanoHTTPD on port 8093) exposes the app's workspace directory, a directory listing,
and a CORS proxy to the in-app WebView. That is a file-read and request-forgery primitive, so it is
constrained on four axes:

- **Binding:** the server listens on `127.0.0.1` only. Binding the wildcard address let any device on
  the same Wi-Fi network read the workspace.
- **Authentication:** every `/api/*` request must carry the per-process random token, either in the
  `X-Operit-Workspace-Token` header or in the `token` query parameter. The token is generated at
  startup (`LocalWebServer.apiToken`), injected into served HTML as `window.__operitWorkspaceApiToken`,
  and compared with `MessageDigest.isEqual` so the comparison is constant-time.
- **CORS:** `addCorsHeaders` reflects an `Origin` only when it is a loopback host on port 8093, and it
  never sends `Access-Control-Allow-Credentials`. `Vary: Origin` is always set.
- **Proxy:** only `http`/`https` targets are fetched, and the relayed request drops the hop-by-hop
  headers, `cookie`, and this server's own token header; the upstream response's framing headers are
  dropped as well.

Token check, CORS allowlist, proxy scheme and the two header denylists live in
`app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/workspace/WorkspaceAccessPolicy.kt`,
which has no Android or NanoHTTPD types and is covered by
`app/src/test/.../workspace/WorkspaceAccessPolicyTest.kt` (run by `:app:testCloneDebugUnitTest`).
`LocalWebServer` only delegates to it. Change a rule there, not in the server.
- **Path containment:** every check that a path stays inside a root uses
  `rootDir.path + File.separator` - see `LocalWebServer.isInRoot` in
  `app/src/main/java/com/ai/assistance/operit/ui/features/chat/webview/LocalWebServer.kt` and
  `WorkspaceDocumentsProvider.isInside` in
  `app/src/main/java/com/ai/assistance/operit/provider/WorkspaceDocumentsProvider.kt`.
  `path.startsWith(rootDir.path)` alone accepts a sibling such as `/sdcard/Operit-workspace-evil`
  for the root `/sdcard/Operit-workspace`.

The proxy forwards no cookies, and it drops the `host`, `connection`, `content-length`,
`accept-encoding`, `cookie`, and token headers from the request it relays.

The token gate covers `/api/*` only. Static workspace routes (`/index.html`, an image, a previewed PDF)
are served without a token because the WebView loads them as plain URLs and their subresources would
each need one; they are protected by the loopback bind plus the containment checks above. The residual
risk is therefore another app on the same device reading workspace files over `127.0.0.1:8093` — the
request-forgery and directory-listing primitives, which are the ones worth stealing, are token-gated.

## The external HTTP servers authenticate with a bearer token

`ExternalChatHttpServer` and `WebChatHttpBridge` serve the LAN-facing chat API, and both refuse a
request unless `Authorization: Bearer <token>` matches the token the user configured. The token comes
from `ExternalHttpApiPreferences.getBearerToken()` (DataStore, app-private). A blank configured token
is rejected outright with `Bearer token not configured`, so the servers never fall open when the
setting is empty.

Extraction and comparison live in
`app/src/main/java/com/ai/assistance/operit/integrations/http/BearerToken.kt`: the header name and the
`Bearer` scheme are matched case-insensitively, whitespace is trimmed, any other scheme yields no
token, and the comparison uses `MessageDigest.isEqual` so it is constant-time. Each server only builds
its own 401 body. `app/src/test/java/com/ai/assistance/operit/integrations/http/BearerTokenTest.kt`
pins those rules, which used to be duplicated verbatim in both servers.

## Exported subpacks are signed with a per-device identity

`KeyStoreHelper.getOrCreateSigningIdentity(context)` generates a random RSA-2048 keypair and a
self-signed certificate on first use and stores them in `filesDir/subpack_signing.p12` under a random
password kept in the app's private `SharedPreferences`. Nothing about that identity leaves the device.

Do not reintroduce a keystore under `app/src/main/assets/`: a keystore shipped in the APK is a private
key published to everyone who downloads the app, and every exported subpack would then share one
signature — enough for anyone to build an APK that Android treats as an update to a victim's installed
export. The consequence of per-device identities is intended: an APK exported on one device is not an
update for the same app exported on another.

## TLS

Model HTTP clients use the platform's default certificate and hostname validation. The HTTP tool's
`ignore_ssl=true` parameter exists for self-signed local endpoints, and it is restricted: the bypass
is only installable for loopback and private-network hosts, so a prompt or a script cannot use it to
disable certificate verification for internet traffic. Every accepted use is logged at error level.
Do not ship a trust-all `TrustManager` or a `hostnameVerifier` that returns `true` in code that sends
API keys.

## Cryptography

The JS bridge (`JsNativeInterfaceDelegates.crypto`) supports:

- `sha256` for anything security-relevant;
- `AES/GCM/NoPadding` with a 12-byte IV and a 128-bit tag, in the layout
  `base64(IV || ciphertext+tag)`. GCM is authenticated, so a modified ciphertext fails instead of
  decrypting to attacker-chosen plaintext. ECB and unauthenticated CBC are not exposed.
- `md5` **only** as a compatibility shim: the bundled bilibili and jmcomic tools must match
  third-party MD5 signatures. Never use it for passwords, tokens, or integrity checks.

## Dependencies

`zod` is pinned to the 4.x line through the `overrides` block in `pnpm-workspace.yaml`
(`zod: ^4.6.7`), and `pnpm-lock.yaml` resolves `4.6.7`. The 3.x line ends at `3.25.76`, which carries
the memory-exhaustion advisory for oversized constrained collections, so an override written as
`"^3.25.76 || ^4.6.6"` is not a pin: npm range semantics let it resolve to the vulnerable 3.x version.
`@modelcontextprotocol/sdk` 1.31.0 declares `zod: ^3.25 || ^4.0`, so the major upgrade is supported.

Keep the override and the `overrides:` block recorded inside `pnpm-lock.yaml` in sync — pnpm copies the
workspace overrides into the lockfile, and `pnpm install --frozen-lockfile` refuses a lockfile whose
overrides no longer match. Never hand-edit a resolved version in `snapshots:` without the matching
entry in `packages:`; that is exactly how the lockfile ended up referencing a `zod@3.25.76` that it did
not define. `ci/test/test_dependency_lockfile.py` enforces both rules and runs in the
`Check gate implementation` step of `.github/workflows/pr-check.yml`; it needs neither pnpm nor PyYAML.

Every tracked `package-lock.json` resolves tarballs from `registry.npmjs.org`. Three lockfiles had
drifted onto `registry.npmmirror.com` and `repo.huaweicloud.com`, which makes `npm ci` depend on a
third-party mirror being reachable and keeping the same paths. The `integrity` hash is what authorizes
the unpacked content, so pointing the same path at the official registry does not change what is
installed. The same test file fails if a mirror host comes back.

Package managers are split on purpose: **npm** drives the root tooling, `web-chat`, and
`examples/toolpkg_wasm_demo` (that is what CI runs: `npm ci`, `npm run build:webchat`,
`npm run build:examples:github`), while **pnpm** owns the `tools/mcp_bridge` workspace declared in
`pnpm-workspace.yaml` and is what `tools/example_packages/sync_example_packages.py` shells out to
(`pnpm exec tsc`, `pnpm build`) — the app also rewrites `npx` to `pnpm dlx` when it launches Node MCP
servers, so `pnpm` has to exist in the Linux terminal at runtime. Do not add a second lockfile for a
directory that already has one.
