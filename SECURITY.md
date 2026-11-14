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
`web-chat/src/ui/util/sanitizeChatHtml.ts`, which rebuilds it from an allowlist of tags and
attributes. Never pass chat content to `dangerouslySetInnerHTML` without sanitizing it first: the
content is attacker-influenced whenever a prompt, a tool result, or a scraped page reaches it.

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

`zod` is pinned to `4.6.7` through the `overrides` block in `pnpm-workspace.yaml`. The 3.x line ends
at `3.25.76`, which carries the memory-exhaustion advisory for oversized constrained collections;
`@modelcontextprotocol/sdk` 1.31.0 declares `zod: ^3.25 || ^4.0`, so the major upgrade is supported.
